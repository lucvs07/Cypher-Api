#!/usr/bin/env bash
# Reproduz localmente o cadastro de teste da empresa `demo`: gestor, câmeras
# reais da meta indústria (Fresa 1/2, Tenda, Sala 4, SICK), webcam do
# notebook, equipes/operadores da E5 e histórico sintético de gamificação.
#
# Por quê um script em vez de commitar data/cypherdb.mv.db: o .gitignore do
# projeto já bloqueia data/ de propósito ("contém tokens de estação e api
# keys de câmera, nunca deve ser commitado") — o banco local carrega esses
# segredos em texto puro. Este script reconstrói os MESMOS dados de teste
# sem nenhum segredo fixo no git: tokens/api-keys são gerados pelo próprio
# backend a cada execução; a senha do GESTOR/operadores vem de variável de
# ambiente (com default óbvio de dev, nunca use em produção).
#
# Pré-requisitos:
#   - Backend `cypher` já rodando em $CYPHER_API_URL (default localhost:8080)
#     — precisa estar de pé porque cria estação/câmera/equipe/usuário via API.
#   - `data/cypherdb.mv.db` já existe com a empresa `demo` semeada pelo
#     CatalogSeeder (acontece sozinho no primeiro boot do backend).
#   - java com o H2 jar do projeto no classpath (usa o mesmo m2 do build) —
#     só para os dois passos que a API não cobre: criar o GESTOR de uma
#     empresa que já existe (POST /setup recusa com 409) e inserir o
#     histórico retroativo de heartbeat/incidente (a API só grava "agora").
#
# Uso:
#   cd /home/asaas/projects/cypher
#   CYPHER_DEMO_GESTOR_PASSWORD=troque-isto ./scripts/seed-empresa-demo.sh
#
# Idempotência: estações/câmeras/equipes/operadores são idempotentes (a API
# devolve 409 se já existirem, o script ignora e segue). O histórico de
# gamificação (Tarefa 5) NÃO é — rode uma vez só; ver o aviso no cabeçalho
# de scripts/seed_gamificacao_demo.py se precisar refazer.

set -euo pipefail
cd "$(dirname "$0")/.."

API_URL="${CYPHER_API_URL:-http://localhost:8080/api/v1}"
EMPRESA="demo"
GESTOR_USER="gestor@${EMPRESA}"
GESTOR_PASSWORD="${CYPHER_DEMO_GESTOR_PASSWORD:?defina CYPHER_DEMO_GESTOR_PASSWORD (senha do gestor@demo a criar)}"
COOKIE_JAR="$(mktemp)"
trap 'rm -f "$COOKIE_JAR"' EXIT

H2_JAR="$(find "${HOME}/.m2/repository/com/h2database/h2" -iname 'h2-2*.jar' 2>/dev/null | sort -V | tail -1)"
if [ -z "$H2_JAR" ]; then
  echo "h2 jar não encontrado em ~/.m2 — rode 'mvn dependency:resolve' primeiro." >&2
  exit 1
fi
DB_URL="jdbc:h2:file:./data/cypherdb;AUTO_SERVER=TRUE"

h2_sql() {
  java -cp "$H2_JAR" org.h2.tools.Shell -url "$DB_URL" -user sa -password "" -sql "$1"
}

echo "==> Verificando se gestor@${EMPRESA} já existe..."
if h2_sql "SELECT COUNT(*) FROM usuarios WHERE username='${GESTOR_USER}';" | grep -q '^1$'; then
  echo "    já existe, pulando criação."
else
  echo "==> Gerando hash bcrypt e criando ${GESTOR_USER}..."
  CP="$(find "${HOME}/.m2/repository/org/springframework/security/spring-security-crypto" -iname '*.jar' | sort -V | tail -1):$(find "${HOME}/.m2/repository/org/springframework/spring-core" -iname '*.jar' | sort -V | tail -1):$(find "${HOME}/.m2/repository/commons-logging/commons-logging" -iname '*.jar' | sort -V | tail -1)"
  HASH="$(echo "System.out.println(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(\"${GESTOR_PASSWORD}\"));" | jshell --class-path "$CP" -s - 2>/dev/null | tail -1)"
  h2_sql "INSERT INTO usuarios (username, password_hash, papel, estacao_id, empresa_id) VALUES ('${GESTOR_USER}', '${HASH}', 'GESTOR', NULL, '${EMPRESA}');"
fi

echo "==> Login como ${GESTOR_USER}..."
curl -sf -X POST "${API_URL}/auth/login" -H "Content-Type: application/json" \
  -d "{\"username\":\"${GESTOR_USER}\",\"password\":\"${GESTOR_PASSWORD}\"}" \
  -c "$COOKIE_JAR" -o /dev/null

post() {
  # post <path> <json-body>  — 2xx ou 409 (já existe) contam como sucesso.
  local path="$1" body="$2" code
  code=$(curl -s -o /tmp/seed-resp.json -w "%{http_code}" -X POST "${API_URL}${path}" \
    -b "$COOKIE_JAR" -H "Content-Type: application/json" -d "$body")
  if [ "$code" != "409" ] && [ "${code:0:1}" != "2" ]; then
    echo "FALHA (${code}) em POST ${path}: $(cat /tmp/seed-resp.json)" >&2
    exit 1
  fi
  echo "    ${path} -> ${code}"
}

echo "==> Estações reais da meta indústria..."
post /stations '{"id":"EST-FRESA1","nome":"Fresa 1","setor":"usinagem","ativa":true}'
post /stations '{"id":"EST-FRESA2","nome":"Fresa 2","setor":"usinagem","ativa":true}'
post /stations '{"id":"EST-TENDA","nome":"Tenda","ativa":true}'
post /stations '{"id":"EST-SALA4","nome":"Sala 4","ativa":true}'
post /stations '{"id":"EST-SICK","nome":"SICK Visionary-B","ativa":true}'
post /stations '{"id":"EST-WEBCAM","nome":"Webcam do notebook","setor":"testes","ativa":true}'

echo "==> Câmeras (fonte com placeholder \${CYPHER_RTSP_USER}/\${CYPHER_RTSP_PASS} — segredo real só no .env do cypher-capture)..."
post /cameras '{"id":"CAM-FRESA1","nome":"Fresa 1","estacaoId":"EST-FRESA1","streamUrl":"http://127.0.0.1:8094/stream","fonte":"rtsp://${CYPHER_RTSP_USER}:${CYPHER_RTSP_PASS}@10.14.22.97:554/cam/realmonitor?channel=1&subtype=0","ativa":true}'
post /cameras '{"id":"CAM-FRESA2","nome":"Fresa 2","estacaoId":"EST-FRESA2","streamUrl":"http://127.0.0.1:8095/stream","fonte":"rtsp://${CYPHER_RTSP_USER}:${CYPHER_RTSP_PASS}@10.14.22.98:554/cam/realmonitor?channel=1&subtype=0","ativa":true}'
post /cameras '{"id":"CAM-TENDA","nome":"Tenda","estacaoId":"EST-TENDA","streamUrl":"http://127.0.0.1:8096/stream","fonte":"rtsp://${CYPHER_RTSP_USER}:${CYPHER_RTSP_PASS}@10.14.22.99:554/cam/realmonitor?channel=1&subtype=0","ativa":true}'
post /cameras '{"id":"CAM-SALA4","nome":"Sala 4","estacaoId":"EST-SALA4","streamUrl":"http://127.0.0.1:8097/stream","fonte":"rtsp://${CYPHER_RTSP_USER}:${CYPHER_RTSP_PASS}@10.14.24.6:554/cam/realmonitor?channel=1&subtype=0","ativa":true}'
post /cameras '{"id":"CAM-SICK","nome":"SICK Visionary-B","estacaoId":"EST-SICK","streamUrl":"http://127.0.0.1:8098/stream","fonte":"rtsp://10.14.22.96:554/video","ativa":true}'
post /cameras '{"id":"CAM-WEBCAM","nome":"Webcam do notebook","estacaoId":"EST-WEBCAM","streamUrl":"http://127.0.0.1:8099/stream","fonte":"0","ativa":true}'

echo "==> Equipes de teste da E5 (placar com 3 estados: melhora, piora, dados insuficientes)..."
post /teams '{"id":"EQ-DEMOA","nome":"Turno Manha - Producao","turno":"MANHA","estacaoIds":["EST-01","EST-02"],"ativa":true,"metaConformidade":0.95,"metaZeroSeveridade4Ativa":true,"metaTempoReconhecimentoSegundos":180}'
post /teams '{"id":"EQ-DEMOB","nome":"Turno Tarde - Producao","turno":"TARDE","estacaoIds":["EST-03","EST-04"],"ativa":true,"metaConformidade":0.90,"metaZeroSeveridade4Ativa":false}'
post /teams '{"id":"EQ-DEMOC","nome":"Turno Noite - Cobertura pendente","turno":"NOITE","estacaoIds":["EST-01","EST-02"],"ativa":true}'

echo "==> Operadores (username/senha via variáveis de ambiente, default de dev óbvio)..."
post /users "{\"username\":\"operador.esteira\",\"papel\":\"OPERADOR\",\"password\":\"${CYPHER_DEMO_OPERADOR_PASSWORD:-operador123}\",\"estacaoId\":\"EST-01\"}"
post /users "{\"username\":\"operador.prensa\",\"papel\":\"OPERADOR\",\"password\":\"${CYPHER_DEMO_OPERADOR_PASSWORD:-operador123}\",\"estacaoId\":\"EST-02\"}"
post /users "{\"username\":\"operador.expedicao\",\"papel\":\"OPERADOR\",\"password\":\"${CYPHER_DEMO_OPERADOR_PASSWORD:-operador123}\",\"estacaoId\":\"EST-03\"}"
post /users "{\"username\":\"operador.solda\",\"papel\":\"OPERADOR\",\"password\":\"${CYPHER_DEMO_OPERADOR_PASSWORD:-operador123}\",\"estacaoId\":\"EST-04\"}"

echo "==> Histórico sintético de gamificação (MonitoramentoDiario + Alerta, últimos 14 dias)..."
SEED_SQL="$(mktemp --suffix=.sql)"
python3 scripts/seed_gamificacao_demo.py > "$SEED_SQL"
h2_sql "RUNSCRIPT FROM '${SEED_SQL}';"
rm -f "$SEED_SQL"

echo "==> Pronto. GET ${API_URL}/placar (logado como gestor@${EMPRESA}) já deve mostrar EQ-DEMOA/B/C."
