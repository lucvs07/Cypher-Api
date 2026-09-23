#!/usr/bin/env bash
# Prepara a empresa `demo` para a banca final da SPI em cima do banco local
# atual (data/cypherdb.mv.db), sem recriar nada: fecha os alertas testados que
# ficaram abertos, poe as equipes do placar nas estacoes reais e regera a
# cobertura/historico sintetico relativos a hoje. Detalhes em
# scripts/demo_banca.py.
#
# Pode rodar com o backend no ar (o H2 esta em AUTO_SERVER) ou parado, e pode
# rodar mais de uma vez: o que e sintetico e apagado e recriado.
#
# Uso:
#   cd ~/projects/cypher
#   ./scripts/preparar-demo-banca.sh
#
# Antes de mexer, grava um backup consistente em data.bak-<data-hora>/ (fora
# de data/, gitignorado). Para voltar: pare o backend e restaure com
#   java -cp <h2.jar> org.h2.tools.Restore -file data.bak-<...>/cypherdb.zip -dir data -db cypherdb
#
# Variaveis (opcionais):
#   CYPHER_API_URL                default: http://localhost:8080/api/v1
#   CYPHER_DEMO_GESTOR_PASSWORD   default: demo12345 (so para conferir o placar no fim)

set -euo pipefail
cd "$(dirname "$0")/.."

API_URL="${CYPHER_API_URL:-http://localhost:8080/api/v1}"
GESTOR_PASSWORD="${CYPHER_DEMO_GESTOR_PASSWORD:-demo12345}"
DB_URL="jdbc:h2:file:./data/cypherdb;AUTO_SERVER=TRUE"

[ -f data/cypherdb.mv.db ] || { echo "data/cypherdb.mv.db nao existe — suba o backend uma vez antes." >&2; exit 1; }

H2_JAR="$(find "${HOME}/.m2/repository/com/h2database/h2" -iname 'h2-2*.jar' ! -iname '*-sources.jar' 2>/dev/null | sort -V | tail -1)"
[ -n "$H2_JAR" ] || { echo "h2 jar nao encontrado em ~/.m2 — rode './mvnw dependency:resolve' primeiro." >&2; exit 1; }

h2_sql() {
  java -cp "$H2_JAR" org.h2.tools.Shell -url "$DB_URL" -user sa -password "" -sql "$1"
}

BACKUP_DIR="data.bak-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"
echo "==> Backup em ${BACKUP_DIR}/cypherdb.zip"
h2_sql "BACKUP TO '${BACKUP_DIR}/cypherdb.zip';" >/dev/null

SQL="$(mktemp --suffix=.sql)"
COOKIE_JAR="$(mktemp)"
RESPOSTA="$(mktemp)"
trap 'rm -f "$SQL" "$COOKIE_JAR" "$RESPOSTA"' EXIT

echo "==> Aplicando scripts/demo_banca.py"
python3 scripts/demo_banca.py > "$SQL"
h2_sql "RUNSCRIPT FROM '${SQL}';"

echo "==> Conferencia no banco"
h2_sql "SELECT COUNT(*) AS alertas_abertos FROM alertas WHERE empresa_id='demo' AND closed_reason IS NULL;"
h2_sql "SELECT COUNT(*) AS alertas_testados FROM alertas WHERE empresa_id='demo' AND run_id <> 'seed-banca';"

if curl -sf "${API_URL}/health" >/dev/null 2>&1; then
  echo "==> Placar (GET /placar como gestor@demo)"
  if curl -sf -X POST "${API_URL}/auth/login" -H "Content-Type: application/json" \
       -d "{\"username\":\"gestor@demo\",\"password\":\"${GESTOR_PASSWORD}\"}" \
       -c "$COOKIE_JAR" -o /dev/null \
     && curl -sf "${API_URL}/placar" -b "$COOKIE_JAR" -o "$RESPOSTA"; then
    python3 - "$RESPOSTA" <<'EOF'
import json, sys
for e in json.load(open(sys.argv[1])):
    if not e["dadosSuficientes"]:
        print(f"  {e['nome']:<40} dados insuficientes (cobertura {e['cobertura']:.0%})")
        continue
    delta = e["deltaConformidadeSemana"]
    tendencia = "sem semana anterior" if delta is None else f"{'melhora' if delta >= 0 else 'piora'} {delta * 100:+.2f} pp"
    print(f"  {e['nome']:<40} conformidade {e['conformidade']:.1%}  {tendencia}  "
          f"sequencia sem queda {e['sequenciaAtual']}d")
EOF
  else
    echo "    nao consegui logar/ler o placar — confira a senha em CYPHER_DEMO_GESTOR_PASSWORD" >&2
  fi
else
  echo "==> Backend fora do ar: suba-o e confira /placar no front."
fi

echo "==> Pronto."
