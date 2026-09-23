"""
Gera o SQL que prepara a empresa `demo` para a banca final da SPI, em cima do
banco local que ja tem os alertas reais dos testes (08, 14, 16 e 20/09).

Diferente de seed_gamificacao_demo.py, nao cria banco do zero: mantem tudo o
que ja esta la e so faz o que a apresentacao precisa.

1. Fecha os alertas testados que ficaram abertos (pipeline parou sem mandar o
   fechamento), com PIPELINE_STOPPED e fim no ultimo alerta da mesma
   execucao. Sem isto eles aparecem como incidentes ativos no painel.
2. Remove o historico sintetico antigo (run_id seed-run-1 e o heartbeat
   sintetico das CAM-01..04), que foi datado no dia em que foi gerado e hoje
   cai fora das duas semanas que o placar compara.
3. Equipes do placar nas estacoes reais, para os alertas testados contarem:
     EQ-DEMOA  Usinagem - Manha    MANHA  Fresa 1 + Fresa 2   alertas reais
     EQ-DEMOB  Montagem - Manha    MANHA  Tenda + Sala 4      alertas reais
     EQ-DEMOC  Usinagem - Noite    NOITE  Fresa 1 + Fresa 2   sem cobertura -> "dados insuficientes"
     EQ-DEMOD  Producao - Tarde    TARDE  EST-03 + EST-04     sintetica, melhora semana a semana
   A EQ-DEMOD existe so porque os alertas reais caem todos numa semana: sem
   ela o placar nao teria nenhuma equipe melhorando. Os incidentes dela ficam
   nas cameras genericas (CAM-03/04, videos de dataset), nunca nas reais. O
   placar so conta camera ativa, entao as duas sao reativadas; com
   --orchestrator o pipeline passa a rodar cenario3.mp4/cenario4.mp4 nelas.
4. Cobertura (monitoramento_diario) dos ultimos 14 dias, relativa a hoje,
   para as equipes com cobertura. Nas cameras reais so entra onde nao ha
   heartbeat real para o mesmo dia e turno.

Rodavel mais de uma vez: tudo o que este script cria e apagado antes de ser
recriado. Alertas sinteticos usam run_id 'seed-banca'; linhas sinteticas de
monitoramento_diario tem ultimo_heartbeat terminando em .987654 segundos, que
um heartbeat real nao produz na pratica.

Uso (feito pelo preparar-demo-banca.sh):
    python3 scripts/demo_banca.py > /tmp/banca.sql

CYPHER_DEMO_HOJE=AAAA-MM-DD troca o "hoje" (so para ensaiar outra data).
"""
from __future__ import annotations

import datetime as dt
import os

TZ = dt.timezone(dt.timedelta(hours=-3))
EMPRESA = "demo"
RUN_SINTETICO = "seed-banca"
MARCA_MICROS = 987654
DIAS = 14
SEGUNDOS_POR_TURNO = 21600  # 6 h das 8 h nominais: 75% de cobertura

CAMERAS_GENERICAS = ["demo:CAM-01", "demo:CAM-02", "demo:CAM-03", "demo:CAM-04"]

# (id, nome, turno, estacoes, meta_conformidade, meta_zero_sev4, meta_reconhecimento_s, cameras_com_cobertura)
EQUIPES = [
    ("demo:EQ-DEMOA", "Usinagem - Manha", "MANHA", ["demo:EST-FRESA1", "demo:EST-FRESA2"],
     0.95, True, 180, ["demo:CAM-FRESA1", "demo:CAM-FRESA2"]),
    ("demo:EQ-DEMOB", "Montagem - Manha", "MANHA", ["demo:EST-TENDA", "demo:EST-SALA4"],
     0.97, True, 300, ["demo:CAM-TENDA", "demo:CAM-SALA4"]),
    ("demo:EQ-DEMOC", "Usinagem - Noite (cobertura pendente)", "NOITE",
     ["demo:EST-FRESA1", "demo:EST-FRESA2"], None, None, None, []),
    ("demo:EQ-DEMOD", "Producao - Tarde", "TARDE", ["demo:EST-03", "demo:EST-04"],
     0.9, False, None, ["demo:CAM-03", "demo:CAM-04"]),
]

# Horarios dentro do turno tanto em -03 quanto em UTC (TurnoWindow usa a hora
# do OffsetDateTime devolvido, e as linhas do pipeline estao em +00).
HORA_ULTIMO_HEARTBEAT = {"MANHA": dt.time(10, 30), "TARDE": dt.time(18, 30)}


def q(valor) -> str:
    if valor is None:
        return "NULL"
    if isinstance(valor, bool):
        return "TRUE" if valor else "FALSE"
    if isinstance(valor, (int, float)):
        return repr(valor)
    return "'" + str(valor).replace("'", "''") + "'"


def ts(d: dt.datetime) -> str:
    return "TIMESTAMP WITH TIME ZONE '" + d.isoformat(sep=" ") + "'"


def fechar_abertos() -> list[str]:
    # Fim = ultimo instante visto na mesma execucao (run_id), quando o
    # pipeline ainda estava de pe; sem nada depois, fica o proprio inicio.
    return [f"""
UPDATE alertas a SET
  encerrado_em = GREATEST(a.data_hora, COALESCE((
      SELECT MAX(COALESCE(b.encerrado_em, b.data_hora)) FROM alertas b
      WHERE b.empresa_id = a.empresa_id AND b.run_id = a.run_id), a.data_hora)),
  closed_reason = 'PIPELINE_STOPPED'
WHERE a.empresa_id = {q(EMPRESA)} AND a.closed_reason IS NULL AND a.run_id <> {q(RUN_SINTETICO)};""",
            f"""
UPDATE alertas SET duracao_segundos =
  DATEDIFF('MILLISECOND', data_hora, encerrado_em) / 1000.0
WHERE empresa_id = {q(EMPRESA)} AND closed_reason = 'PIPELINE_STOPPED'
  AND duracao_segundos IS NULL AND encerrado_em IS NOT NULL;"""]


def limpar_sintetico() -> list[str]:
    runs = f"({q('seed-run-1')}, {q(RUN_SINTETICO)})"
    filhos = f"SELECT id FROM alertas WHERE empresa_id = {q(EMPRESA)} AND run_id IN {runs}"
    genericas = ", ".join(q(c) for c in CAMERAS_GENERICAS)
    return [
        f"DELETE FROM alerta_epi_missing WHERE alerta_id IN ({filhos});",
        f"DELETE FROM alerta_epi_present WHERE alerta_id IN ({filhos});",
        f"DELETE FROM alerta_leitura WHERE alerta_id IN ({filhos});",
        f"DELETE FROM incident_llm_analyses WHERE alerta_id IN ({filhos});",
        f"DELETE FROM alertas WHERE empresa_id = {q(EMPRESA)} AND run_id IN {runs};",
        # Heartbeat das genericas era todo sintetico (seed antigo).
        f"DELETE FROM monitoramento_diario WHERE empresa_id = {q(EMPRESA)} AND camera_id IN ({genericas});",
        # Sintetico deste script nas cameras reais: so as linhas marcadas.
        f"DELETE FROM monitoramento_diario WHERE empresa_id = {q(EMPRESA)} "
        f"AND MOD(EXTRACT(MICROSECOND FROM ultimo_heartbeat), 1000000) = {MARCA_MICROS};",
    ]


def equipes() -> list[str]:
    out = [f"UPDATE cameras SET ativa = TRUE WHERE empresa_id = {q(EMPRESA)} "
           f"AND id IN ({q('demo:CAM-03')}, {q('demo:CAM-04')});"]
    for eid, nome, turno, estacoes, meta, zero4, reconhecimento, _ in EQUIPES:
        out.append(
            "MERGE INTO equipes (id, ativa, empresa_id, nome, turno, meta_conformidade, "
            "meta_tempo_reconhecimento_segundos, meta_zero_severidade4_ativa) KEY (id) VALUES "
            f"({q(eid)}, TRUE, {q(EMPRESA)}, {q(nome)}, {q(turno)}, {q(meta)}, {q(reconhecimento)}, {q(zero4)});")
        out.append(f"DELETE FROM equipe_estacoes WHERE equipe_id = {q(eid)};")
        for est in estacoes:
            out.append(f"INSERT INTO equipe_estacoes (equipe_id, estacao_id) VALUES ({q(eid)}, {q(est)});")
    return out


def cobertura(hoje: dt.date) -> list[str]:
    out = []
    for _, _, turno, _, _, _, _, cameras in EQUIPES:
        for cam in cameras:
            for i in range(DIAS):
                dia = hoje - dt.timedelta(days=i)
                ultimo = dt.datetime.combine(dia, HORA_ULTIMO_HEARTBEAT[turno], tzinfo=TZ).replace(
                    microsecond=MARCA_MICROS)
                # So onde nao ha heartbeat real do mesmo dia e turno: nao
                # infla a cobertura medida de verdade nos dias de teste.
                out.append(
                    "INSERT INTO monitoramento_diario (camera_id, empresa_id, dia, turno, "
                    "segundos_monitorados, ultimo_heartbeat) "
                    f"SELECT {q(cam)}, {q(EMPRESA)}, DATE '{dia.isoformat()}', {q(turno)}, "
                    f"{SEGUNDOS_POR_TURNO}, {ts(ultimo)} FROM DUAL WHERE NOT EXISTS ("
                    f"SELECT 1 FROM monitoramento_diario WHERE empresa_id = {q(EMPRESA)} "
                    f"AND camera_id = {q(cam)} AND dia = DATE '{dia.isoformat()}' AND turno = {q(turno)});")
    return out


def alertas_sinteticos(hoje: dt.date) -> list[str]:
    """EQ-DEMOD: mais incidente na semana anterior, menos na atual (melhora)."""
    seq = 0
    out = []

    def add(cam, dias_atras, hora, dur, reba):
        nonlocal seq
        seq += 1
        inicio = dt.datetime.combine(hoje - dt.timedelta(days=dias_atras), hora, tzinfo=TZ)
        fim = inicio + dt.timedelta(seconds=dur)
        codigo = cam.split(":", 1)[1]
        key = f"{cam}:{RUN_SINTETICO}:track-{seq}:{inicio.astimezone(dt.timezone.utc):%Y%m%dT%H%M%SZ}"
        out.append(
            "INSERT INTO alertas (incident_key, run_id, track_id, camera_id, tipo, severidade, nivel, descricao, "
            "reason, postura_categoria, reba_score, action_level, zona, zone_status, snapshot_url, data_hora, "
            "encerrado_em, duracao_segundos, severidade_pico, closed_reason, status, empresa_id) VALUES ("
            f"{q(key)}, {q(RUN_SINTETICO)}, {seq}, {q(cam)}, 'ERGONOMIC_RISK', 2, 'MEDIO', "
            f"{q('Postura inadequada sustentada em ' + codigo)}, {q(f'reba_{reba}')}, 'ergonomically_inadequate', "
            f"{reba}, 3, NULL, 'outside', NULL, {ts(inicio)}, {ts(fim)}, {dur}, 2, 'STATE_EXIT', 'RESOLVIDO', "
            f"{q(EMPRESA)});")

    # Semana anterior (8 a 13 dias atras): 4 episodios longos.
    add("demo:CAM-03", 13, dt.time(16, 10), 2400, 10)
    add("demo:CAM-04", 11, dt.time(17, 20), 1800, 9)
    add("demo:CAM-03", 10, dt.time(16, 40), 2100, 11)
    add("demo:CAM-04", 8, dt.time(18, 5), 1500, 9)
    # Semana atual: 1 episodio curto.
    add("demo:CAM-03", 3, dt.time(17, 15), 420, 8)
    return out


def main() -> None:
    hoje = (dt.date.fromisoformat(os.environ["CYPHER_DEMO_HOJE"]) if os.environ.get("CYPHER_DEMO_HOJE")
            else dt.datetime.now(TZ).date())
    partes = [
        "-- gerado por scripts/demo_banca.py em " + dt.datetime.now(TZ).isoformat(timespec="seconds"),
        *fechar_abertos(),
        *limpar_sintetico(),
        *equipes(),
        *cobertura(hoje),
        *alertas_sinteticos(hoje),
    ]
    print("\n".join(partes))


if __name__ == "__main__":
    main()
