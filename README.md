# Cypher — backend de segurança industrial

Cypher é uma plataforma de monitoramento de segurança industrial por visão computacional — detecta uso incorreto de EPI, postura de risco e quedas em tempo real, construída para a Metaindústria no Challenge SPI/FIAP. O sistema é composto por três repositórios: [`cypher-capture`](https://github.com/lucvs07/cypher-capture) (Python/YOLO, decide quando um incidente começa e termina, um processo por câmera), **`cypher`** (este repositório — Spring Boot 4/Java 21, recebe, persiste e expõe incidentes, autenticação, papéis, multi-tenant, relatórios e análise por LLM) e [`cypher-front`](https://github.com/Ronaldovels/cypher-front) (Angular, dashboard/heatmap/timeline/gamificação). Este README documenta apenas o backend.

## Domínio

O modelo vive em `src/main/java/com/dev4/cypher/model/`, agrupado por área:

**Identidade e acesso**
- `Empresa` — unidade de isolamento (tenant); `id` é o slug escolhido no sign-up (`POST /api/v1/setup`), imutável.
- `Usuario` — conta autenticada por usuário/senha, papel `GESTOR` ou `OPERADOR`; `estacaoId` só é preenchido para `OPERADOR`.
- `Equipe` — unidade de alocação: cobre uma ou mais estações dentro de um único turno (`Turno`); alocação estática, sem escala por data.

**Catálogo**
- `Estacao` — uma máquina/posto de trabalho, célula do heatmap; coberta por uma ou mais câmeras.
- `Camera` — câmera vinculada a uma estação; carrega `streamUrl` (MJPEG servido pelo pipeline) e `fonte` (o que o pipeline abre: webcam, arquivo ou RTSP).
- `Zona` — zona de risco desenhada sobre uma câmera (`ZoneType`), composta por `PontoNormalizado`.

**Eventos**
- `Alerta` — incidente de segurança detectado pelo pipeline; representa um episódio com início e fim (não um fato pontual), identificado por `incidentKey` (`cameraId:runId:trackId:timestampDeAbertura`) para ingestão idempotente. Campos incluem `AlertaTipo`, `AlertaNivel`, `AlertaStatus`, `ClosedReason`, `NormaAplicavel`.
- `AlertaLeitura` — estado de leitura de uma notificação por um GESTOR (a presença da linha já significa "lido"; sem coluna booleana).
- `IncidentLlmAnalysis` — registro de uma chamada de análise de risco por LLM multimodal sobre o snapshot de um incidente (`AnalysisStatus`, `provider`, `promptVersion`).
- `MonitoramentoDiario` — tempo monitorado agregado por câmera/dia/turno, alimentado pelos heartbeats do pipeline; nunca grava evento a evento.

**Integrações**
- `WebhookSubscription` — assinante externo que recebe incidentes por webhook, com filtro de severidade mínima (`minSeverity`).
- `WebhookDelivery` — registro de uma tentativa de entrega (para responder "o alerta chegou ao cliente?").

Nenhuma dessas entidades usa `@ManyToOne`/FK entre si por design: referências como `Alerta.cameraId` ou `WebhookDelivery.subscriptionId` são soltas de propósito, para aceitar dados de câmeras não cadastradas e simplificar o isolamento por tenant.

## Autenticação e multi-tenancy

Três mecanismos de autenticação coexistem na mesma cadeia de filtros (`SecurityConfig`), sem JWT nem servidor de autenticação separado (implementação em `src/main/java/com/dev4/cypher/security/`):

1. **Chave de API por câmera** (`X-Cypher-Key`) — `CypherKeyAuthenticationFilter` valida o header contra `Camera.apiKey` e autentica como `ROLE_PIPELINE` (`PipelineAuthentication`), sem sessão. Obrigatório nas rotas de ingestão (`POST /api/v1/incidents`, `PATCH /api/v1/incidents/{incidentKey}`) e no heartbeat; uma chave só pode agir sobre incidentes da própria câmera.
2. **Sessão de GESTOR** (cookie `HttpOnly`, `GestorAuthentication`) — login por usuário/senha em `POST /api/v1/auth/login`; acesso completo ao tenant.
3. **Sessão de OPERADOR** (cookie `HttpOnly`, `OperadorAuthentication`) — por usuário/senha vinculado a uma estação ou por token de estação (kiosk, `POST /api/v1/auth/login-estacao`); escopo restrito à própria `Estacao`.

`SessionAuthenticator` é o único ponto que grava `Authentication` na `HttpSession` (usado por `AuthController` e `SetupController`), rotacionando o id da sessão a cada login para evitar fixação de sessão. Cookie foi escolhido em vez de JWT porque o `EventSource` do SSE não aceita header customizado — o mesmo cookie autentica REST e stream.

**Multi-tenancy**: o backend isola dados por `Empresa` via `TenantContext` (`security/TenantContext.java`), que é a única fronteira entre a chave armazenada `"empresaId:codigo"` (chave primária de `Estacao`/`Camera`/`Equipe`/`Zona`/`Alerta`/`WebhookSubscription`) e o código puro que API/UI enxergam. `Usuario.username` segue o padrão `"nome@empresaId"`. O orquestrador do `cypher-capture` é mono-tenant por instância — rodar câmeras de duas empresas exige dois processos, cada um com sua conta `GESTOR`.

## API

Endpoints e proteção, conforme `SecurityConfig` (autoridade única — cada `requestMatchers` ali é uma linha desta tabela; o que não está listado explicitamente cai no catch-all `anyRequest().hasRole("GESTOR")`):

| Método | Rota | Auth | Controller |
|---|---|---|---|
| GET | `/api/v1/health` | público | `HealthController` |
| POST | `/api/v1/setup` | público | `SetupController` — cria `Empresa` + `Usuario` GESTOR mestre |
| POST | `/api/v1/auth/login` | público | `AuthController` — login GESTOR |
| POST | `/api/v1/auth/login-estacao` | público | `AuthController` — login OPERADOR (usuário/senha ou token de estação) |
| POST | `/api/v1/auth/logout` | GESTOR ou OPERADOR | `AuthController` |
| GET | `/api/v1/auth/me` | GESTOR ou OPERADOR | `AuthController` |
| POST | `/api/v1/incidents` | PIPELINE (`X-Cypher-Key`, obrigatório) | `IncidentController` — ingestão, idempotente por `incidentKey` |
| PATCH | `/api/v1/incidents/{incidentKey}` | PIPELINE (`X-Cypher-Key`, obrigatório) | `IncidentController` — fechamento do episódio / snapshot tardio |
| GET | `/api/v1/incidents/stream` | GESTOR ou OPERADOR | `IncidentController` — SSE, escopado por empresa e (se OPERADOR) por estação |
| GET | `/api/v1/incidents`, `/api/v1/incidents/{id}` | GESTOR ou OPERADOR | `IncidentController` — listagem paginada com filtros (`since`, `camera`, `severityMin`, `status`) e item individual |
| PUT | `/api/v1/incidents/{id}/status` | GESTOR ou OPERADOR | `IncidentController` — muda status do incidente |
| POST | `/api/v1/incidents/{id}/analyze` | GESTOR | `IncidentController` — dispara análise de risco por LLM sobre o snapshot |
| GET | `/api/v1/incidents/{id}/analyze` | GESTOR | `IncidentController` — última análise já feita, sem chamar o provedor de novo |
| POST | `/api/v1/cameras/{codigo}/heartbeat` | PIPELINE | `HeartbeatController` — alimenta `MonitoramentoDiario` |
| GET | `/api/v1/cameras/{id}/config` | PIPELINE ou GESTOR | `CatalogController` — superfície de máquina consumida pelo pipeline no boot |
| GET | `/api/v1/cameras/{codigo}/zones/config` | PIPELINE ou GESTOR | `ZonaController` |
| GET | `/api/v1/cameras`, `/api/v1/stations` | GESTOR ou OPERADOR | `CatalogController` — listas ativas para dashboard |
| GET/POST/PUT/DELETE `/api/v1/stations`, `/api/v1/cameras` (admin) | GESTOR (catch-all) | `CatalogController` — CRUD de catálogo, regeneração de token de estação e apiKey de câmera |
| CRUD `/api/v1/cameras/{codigo}/zones` | GESTOR (catch-all) | `ZonaController` |
| CRUD `/api/v1/teams` | GESTOR (catch-all) | `TeamController` |
| CRUD `/api/v1/users` | GESTOR (catch-all) | `UserController` |
| CRUD `/api/v1/webhooks`, GET `/api/v1/webhooks/{id}/deliveries` | GESTOR (catch-all) | `WebhookController` |
| GET `/api/v1/notificacoes`, `/api/v1/notificacoes/contagem-nao-lidas`, POST `/api/v1/notificacoes/marcar-lidas` | GESTOR (catch-all) | `NotificacaoController` |
| GET `/api/v1/placar` | GESTOR (catch-all) | `PlacarController` — placar de gamificação por equipe |
| GET `/api/v1/summary` | GESTOR (catch-all) | `SummaryController` |
| GET `/api/v1/reports/incidents.csv`, `/placar.csv`, `/summary.pdf` | GESTOR (catch-all) | `ReportController` — relatórios em arquivo |

Notas de segurança embutidas no código que valem citar: uma chave `X-Cypher-Key` só pode agir sobre incidentes da própria câmera (`IncidentController.chaveDeOutraCamera`); um OPERADOR nunca vê incidentes de câmera desconhecida ou de outra estação (`IncidentController.foraDoEscopoDoOperador`); o filtro de ingestão casa as rotas sobre o path já decodificado (`PathPatternRequestMatcher`) para fechar bypass por percent-encoding.

## Como rodar localmente

Pré-requisito: Java 21.

```bash
./mvnw spring-boot:run
```

A API sobe em `http://localhost:8080`. H2 embarcado em modo arquivo (`jdbc:h2:file:./data/cypherdb;AUTO_SERVER=TRUE`, ver `src/main/resources/application.yaml`), dados persistem entre reinicializações; `data/` é gitignored (carrega tokens e api-keys em texto puro). Console H2 em `http://localhost:8080/h2-console` (usuário `sa`, senha vazia).

O catálogo inicial (estações/câmeras de exemplo) é semeado por `CatalogProperties`/`CatalogSeeder` a partir de `cypher.catalog` em `application.yaml`. Para dados de demonstração completos (gestor, câmeras reais da meta indústria, equipes, operadores e histórico sintético de gamificação), rode:

```bash
CYPHER_DEMO_GESTOR_PASSWORD=troque-isto ./scripts/seed-empresa-demo.sh
```

Requer o backend já rodando; usa `scripts/seed_gamificacao_demo.py` para o histórico retroativo. Veja os comentários no início do script para pré-requisitos e idempotência.

Variáveis de ambiente relevantes para análise por LLM (`cypher.llm` em `application.yaml`): `CYPHER_CLAUDE_API_KEY`, `CYPHER_GEMINI_API_KEY` (provider default: `claude`).

### Subindo os 3 projetos juntos (backend + front + captura)

Para rodar o sistema completo (não só este backend) com dado real da empresa `demo`, use `scripts/subir-empresa-demo.sh` (presente também em `cypher-front` e `cypher-capture` — pode rodar a partir de qualquer um dos três):

```bash
./scripts/subir-empresa-demo.sh start   # sobe backend, semeia a empresa demo, sobe o front e a webcam
./scripts/subir-empresa-demo.sh status
./scripts/subir-empresa-demo.sh logs
./scripts/subir-empresa-demo.sh stop
```

Por padrão assume os repositórios em `~/projects/cypher`, `~/projects/challenge/cypher-front` e `~/projects/challenge/cypher-capture` (configurável por variável de ambiente — ver cabeçalho do script). Detalhes: [`arquitetura.md`](https://github.com/Ronaldovels/cypher-front/blob/dev/docs/arquitetura.md) do `cypher-front`.

## Stack técnica

Verificado em `pom.xml`:

- Java 21, Spring Boot 4.0.6 (`spring-boot-starter-parent`)
- `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`
- `spring-boot-h2console` + `com.h2database:h2` (runtime) — H2 embarcado, modo arquivo
- `org.apache.commons:commons-csv` 1.11.0 — relatórios CSV
- `com.openhtmltopdf:openhtmltopdf-pdfbox` 1.0.10 — relatório PDF
- Lombok (compile-time, excluído do jar final)
- Testes: `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`

## Estrutura do projeto

```
src/main/java/com/dev4/cypher/
├── catalog/       # validação de código de estação/câmera/zona (CatalogCodes, CatalogValidationException)
├── config/        # SecurityConfig, CorsConfig, LlmProperties, CatalogProperties/CatalogSeeder
├── controller/     # REST controllers (ver tabela de API acima)
├── dto/           # request/response, um por operação
├── event/         # IncidentCreatedEvent, IncidentChangedEvent (Spring ApplicationEvent)
├── llm/           # análise de risco multimodal (ClaudeVisionClient, GeminiVisionClient, PromptBuilder, retry)
├── mapper/        # IncidentMapper (entidade <-> DTO)
├── model/         # entidades JPA (ver Domínio acima)
├── report/        # CsvReportBuilder, PdfReportBuilder
├── repository/     # Spring Data JPA
├── security/       # os três mecanismos de auth + TenantContext + TokenGenerator
├── service/        # regras de negócio
├── sse/            # IncidentSseService — registro em memória de conexões SSE por empresa/estação
└── webhook/         # WebhookDispatcher (fan-out assíncrono com retry) + WebhookSigner (HMAC-SHA256)
```

Suíte de testes em `src/test/java`, cobrindo controllers, services e segurança.

## Vídeo de demonstração

**Link:** `[PREENCHER: link do vídeo]`

Vídeo único, centralizado no repositório principal ([`cypher-front`](https://github.com/Ronaldovels/cypher-front#-vídeo-de-demonstração)), navegando pelas principais telas do sistema com os 3 projetos rodando localmente (dado real da empresa `demo`).

## Gestão ágil (Sprint 3)

Este repositório é secundário na entrega — a documentação de gestão ágil (Trello, cerimônias, roteiro) é centralizada no repositório principal, [`cypher-front`](https://github.com/Ronaldovels/cypher-front).

- Board Trello: https://trello.com/b/AqgHhHCP/dev4-cypher-challenge
- Protótipo Figma: https://www.figma.com/design/kyeXTO2Uw0lkm8DPK2a22v/Interface?node-id=48-2
- Design system: https://github.com/Ronaldovels/cypher-front/blob/dev/docs/design-system.md
- Arquitetura (3 repositórios): https://github.com/Ronaldovels/cypher-front/blob/dev/docs/arquitetura.md
- Fluxo de telas: https://github.com/Ronaldovels/cypher-front/blob/dev/docs/fluxo-de-telas.md
- Repositório principal da entrega: https://github.com/Ronaldovels/cypher-front
