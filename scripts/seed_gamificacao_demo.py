"""
Gera o SQL de histórico sintético de gamificação (E5) para a empresa `demo`
— MonitoramentoDiario + Alerta dos últimos 14 dias, terminando hoje.

Por que SQL direto e não a API: o heartbeat real (POST /cameras/{id}/heartbeat)
só grava `OffsetDateTime.now()` no servidor — não dá pra "voltar no tempo" via
API pra simular dias de cobertura. Este script existe só pra reproduzir
localmente o fixture de teste da E5 (placar com 3 estados: ranking positivo,
ranking negativo, "dados insuficientes") sem esperar dias reais de captura.

Usa só as câmeras genéricas semeadas pelo CatalogSeeder (CAM-01..04) — nunca
as câmeras reais da fábrica (Fresa 1/2, Tenda, Sala 4, SICK), pra não misturar
histórico fictício com o que vai virar histórico de produção real quando o
pipeline rodar de verdade lá.

Uso:
    python3 scripts/seed_gamificacao_demo.py > /tmp/seed.sql
    java -cp <h2.jar> org.h2.tools.Shell -url "jdbc:h2:file:./data/cypherdb;AUTO_SERVER=TRUE" \\
        -user sa -password "" -sql "RUNSCRIPT FROM '/tmp/seed.sql'"

Idempotência: NÃO é idempotente — rodar duas vezes duplica incidentes (chave
única por incident_key evita duplicar exatamente o mesmo dia, mas o dia de
referência muda a cada execução porque é sempre "hoje - N dias"). Rode uma
vez; se precisar refazer, limpe as linhas WHERE empresa_id='demo' AND
run_id='seed-run-1' (alertas) / camera_id IN ('demo:CAM-01', ...) AND
ultimo_heartbeat >= <primeira execução> (monitoramento_diario) antes.
"""
from __future__ import annotations

import datetime as dt

TZ = dt.timezone(dt.timedelta(hours=-3))
EMPRESA = "demo"


def rows_monitoramento(today: dt.date) -> list[tuple[str, dt.date, str, int, dt.datetime]]:
    out = []
    # Equipe A (turno MANHA): CAM-01/CAM-02, ~75% de cobertura, 14 dias.
    for cam in ["demo:CAM-01", "demo:CAM-02"]:
        for i in range(14):
            dia = today - dt.timedelta(days=i)
            ultimo = dt.datetime.combine(dia, dt.time(13, 45), tzinfo=TZ)
            out.append((cam, dia, "MANHA", 21600, ultimo))
    # Equipe B (turno TARDE): CAM-03/CAM-04, ~75% de cobertura, 14 dias.
    for cam in ["demo:CAM-03", "demo:CAM-04"]:
        for i in range(14):
            dia = today - dt.timedelta(days=i)
            ultimo = dt.datetime.combine(dia, dt.time(21, 45), tzinfo=TZ)
            out.append((cam, dia, "TARDE", 21600, ultimo))
    # Equipe C (turno NOITE, mesmas estações de A): de propósito SEM nenhuma
    # linha — cobertura 0 => "dados insuficientes" no placar.
    return out


def rows_alerta(today: dt.date) -> list[dict]:
    out = []
    seq = 1

    def add(cam, dia, hora, tipo, dur, sev, reason, postura, reba, al):
        nonlocal seq
        inicio = dt.datetime.combine(dia, hora, tzinfo=TZ)
        fim = inicio + dt.timedelta(seconds=dur)
        nivel = "ALTO" if sev >= 3 else ("MEDIO" if sev == 2 else "BAIXO")
        key = f"{cam}:seed{seq:03d}:track-1:{inicio.strftime('%Y%m%dT%H%M%SZ')}"
        out.append(dict(key=key, cam=cam, tipo=tipo, sev=sev, nivel=nivel,
                         desc=f"Incidente sintetico de teste ({tipo})", reason=reason,
                         postura=postura, reba=reba, al=al, inicio=inicio, fim=fim, dur=dur))
        seq += 1

    # Equipe A / MANHA — semana anterior: mais incidente (conformidade pior).
    add("demo:CAM-01", today - dt.timedelta(days=13), dt.time(11, 10), "ERGONOMIC_RISK", 1200, 2,
        "reba_9", "ergonomically_inadequate", 9, 3)
    add("demo:CAM-02", today - dt.timedelta(days=11), dt.time(11, 20), "ERGONOMIC_RISK", 1800, 2,
        "reba_10", "ergonomically_inadequate", 10, 3)
    add("demo:CAM-01", today - dt.timedelta(days=9), dt.time(11, 5), "ERGONOMIC_RISK", 600, 2,
        "reba_8", "ergonomically_inadequate", 8, 3)
    # Queda real confirmada (dur >= 3s) — alimenta a métrica de sequência.
    add("demo:CAM-01", today - dt.timedelta(days=10), dt.time(11, 0), "FALL_RISK", 5.0, 4,
        "person_fallen", "immediate_risk", 1, 1)

    # Equipe A / MANHA — semana atual: bem menos incidente (melhora).
    add("demo:CAM-02", today - dt.timedelta(days=5), dt.time(11, 15), "ERGONOMIC_RISK", 300, 2,
        "reba_8", "ergonomically_inadequate", 8, 3)

    # Equipe B / TARDE — semana anterior: pouco incidente (conformidade boa).
    add("demo:CAM-03", today - dt.timedelta(days=12), dt.time(18, 10), "ERGONOMIC_RISK", 300, 2,
        "reba_8", "ergonomically_inadequate", 8, 3)

    # Equipe B / TARDE — semana atual: bem mais incidente (piora).
    add("demo:CAM-03", today - dt.timedelta(days=6), dt.time(18, 5), "ERGONOMIC_RISK", 2000, 2,
        "reba_11", "ergonomically_inadequate", 11, 4)
    add("demo:CAM-04", today - dt.timedelta(days=4), dt.time(18, 20), "ERGONOMIC_RISK", 1500, 2,
        "reba_9", "ergonomically_inadequate", 9, 3)
    add("demo:CAM-03", today - dt.timedelta(days=2), dt.time(18, 15), "ERGONOMIC_RISK", 1500, 2,
        "reba_9", "ergonomically_inadequate", 9, 3)

    return out


def sql_escape(s: str) -> str:
    return s.replace("'", "''")


def fmt_ts(d: dt.datetime) -> str:
    return d.strftime("%Y-%m-%d %H:%M:%S") + "-03:00"


def main() -> None:
    today = dt.date.today()
    lines = []
    for cam, dia, turno, segs, ultimo in rows_monitoramento(today):
        lines.append(
            "INSERT INTO monitoramento_diario (camera_id, empresa_id, dia, turno, segundos_monitorados, ultimo_heartbeat) "
            f"VALUES ('{cam}', '{EMPRESA}', '{dia.isoformat()}', '{turno}', {segs}, TIMESTAMP '{fmt_ts(ultimo)}');"
        )

    for a in rows_alerta(today):
        lines.append(
            "INSERT INTO alertas (incident_key, run_id, track_id, camera_id, tipo, severidade, nivel, descricao, "
            "reason, postura_categoria, reba_score, action_level, zona, zone_status, snapshot_url, data_hora, "
            "encerrado_em, duracao_segundos, severidade_pico, closed_reason, status, empresa_id) VALUES ("
            f"'{sql_escape(a['key'])}', 'seed-run-1', 1, '{a['cam']}', '{a['tipo']}', {a['sev']}, '{a['nivel']}', "
            f"'{sql_escape(a['desc'])}', '{a['reason']}', '{a['postura']}', {a['reba']}, {a['al']}, NULL, 'outside', "
            f"NULL, TIMESTAMP '{fmt_ts(a['inicio'])}', TIMESTAMP '{fmt_ts(a['fim'])}', {a['dur']}, {a['sev']}, "
            f"'STATE_EXIT', 'PENDENTE', '{EMPRESA}');"
        )

    print("\n".join(lines))


if __name__ == "__main__":
    main()
