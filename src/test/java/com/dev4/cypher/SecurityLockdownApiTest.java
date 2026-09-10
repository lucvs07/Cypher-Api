package com.dev4.cypher;

import com.dev4.cypher.repository.CameraRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static com.dev4.cypher.security.AuthTestSupport.loginGestorBootstrap;
import static com.dev4.cypher.security.AuthTestSupport.loginOperador;
import static com.dev4.cypher.security.CypherKeyAuthenticationFilter.HEADER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityLockdownApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CameraRepository cameras;

    @Test
    void healthContinuaPublico() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }

    @Test
    void setupPosConfiguradoDevolve409NaoAutenticacao() throws Exception {
        // Garante que ao menos uma Empresa com o slug "acme-test" já existe
        // (o gestor bootstrap, idempotente — ver AuthTestSupport). A prova
        // aqui é que a resposta é 409 (slug ja cadastrado), não 401: POST
        // /setup continua público mesmo travado, nunca escondido atrás de
        // sessão/papel (spec E4 §2.5/§2.6/§3).
        loginGestorBootstrap(mvc);

        mvc.perform(post("/api/v1/setup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"outro\", \"password\": \"x\", "
                                + "\"empresaId\": \"acme-test\", \"empresaNome\": \"Acme Test\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void summarySemSessaoDevolve401() throws Exception {
        mvc.perform(get("/api/v1/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    void teamsSemSessaoDevolve401() throws Exception {
        mvc.perform(get("/api/v1/teams")).andExpect(status().isUnauthorized());
    }

    @Test
    void usersSemSessaoDevolve401() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void webhooksSemSessaoDevolve401() throws Exception {
        mvc.perform(get("/api/v1/webhooks")).andExpect(status().isUnauthorized());
    }

    @Test
    void notificacoesSemSessaoDevolve401() throws Exception {
        mvc.perform(get("/api/v1/notificacoes")).andExpect(status().isUnauthorized());
    }

    @Test
    void notificacoesContagemNaoLidasSemSessaoDevolve401() throws Exception {
        mvc.perform(get("/api/v1/notificacoes/contagem-nao-lidas")).andExpect(status().isUnauthorized());
    }

    @Test
    void notificacoesMarcarLidasSemSessaoDevolve401() throws Exception {
        mvc.perform(post("/api/v1/notificacoes/marcar-lidas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertaIds\": [1]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void summaryComSessaoGestorDevolve200() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);

        mvc.perform(get("/api/v1/summary").session(gestor)).andExpect(status().isOk());
    }

    @Test
    void summaryComSessaoOperadorDevolve403() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);
        mvc.perform(post("/api/v1/stations").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-95\", \"nome\": \"A\", \"setor\": \"s\"}"));
        String token = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/v1/stations/EST-95/token/regenerate").session(gestor))
                        .andReturn().getResponse().getContentAsString(),
                "$.token");
        MockHttpSession operador = loginOperador(mvc, token);

        mvc.perform(get("/api/v1/summary").session(operador)).andExpect(status().isForbidden());
    }

    @Test
    void webhooksComSessaoOperadorDevolve403() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);
        mvc.perform(post("/api/v1/stations").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-93\", \"nome\": \"A\", \"setor\": \"s\"}"));
        String token = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/v1/stations/EST-93/token/regenerate").session(gestor))
                        .andReturn().getResponse().getContentAsString(),
                "$.token");
        MockHttpSession operador = loginOperador(mvc, token);

        mvc.perform(get("/api/v1/webhooks").session(operador)).andExpect(status().isForbidden());
    }

    @Test
    void notificacoesComSessaoOperadorDevolve403() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);
        mvc.perform(post("/api/v1/stations").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-NOTLOCK\", \"nome\": \"A\", \"setor\": \"s\"}"));
        String token = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/v1/stations/EST-NOTLOCK/token/regenerate").session(gestor))
                        .andReturn().getResponse().getContentAsString(),
                "$.token");
        MockHttpSession operador = loginOperador(mvc, token);

        mvc.perform(get("/api/v1/notificacoes").session(operador)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/notificacoes/contagem-nao-lidas").session(operador))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/notificacoes/marcar-lidas").session(operador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertaIds\": [1]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void camerasEStationsListaAceitamGestorEOperador() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);
        mvc.perform(post("/api/v1/stations").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-96\", \"nome\": \"A\", \"setor\": \"s\"}"));
        // Segunda estação, alheia à do próprio operador — prova a Restrição
        // Global de que a navegação NÃO é filtrada por estacaoId para
        // OPERADOR (ao contrário dos endpoints de incidente).
        mvc.perform(post("/api/v1/stations").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-96B\", \"nome\": \"B\", \"setor\": \"s\"}"));
        mvc.perform(post("/api/v1/cameras").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"CAM-96B\", \"nome\": \"C\", \"estacaoId\": \"EST-96B\", "
                        + "\"streamUrl\": \"http://127.0.0.1:82962/stream\", \"fonte\": \"f\"}"));
        String token = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/v1/stations/EST-96/token/regenerate").session(gestor))
                        .andReturn().getResponse().getContentAsString(),
                "$.token");
        MockHttpSession operador = loginOperador(mvc, token);

        mvc.perform(get("/api/v1/cameras").session(operador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='CAM-96B')]").isNotEmpty());
        mvc.perform(get("/api/v1/stations").session(operador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='EST-96B')]").isNotEmpty());
    }

    @Test
    void operadorComSessaoEChaveValidaJuntasDevolve403() throws Exception {
        // Investigação do achado Minor: o filtro X-Cypher-Key roda depois do
        // filtro que restaura o Authentication da sessão, e SEMPRE
        // sobrescreve o contexto quando a chave bate — mesmo havendo uma
        // sessão OPERADOR válida. GET /incidents exige
        // hasAnyRole("GESTOR","OPERADOR"); ROLE_PIPELINE não está nessa
        // lista, então o resultado observado (e aqui fixado por teste) é
        // 403 — a sessão OPERADOR é silenciosamente substituída, não
        // combinada com o papel PIPELINE. Comportamento atual confirmado,
        // não alterado por esta tarefa.
        MockHttpSession gestor = loginGestorBootstrap(mvc);
        mvc.perform(post("/api/v1/stations").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-98\", \"nome\": \"A\", \"setor\": \"s\"}"));
        mvc.perform(post("/api/v1/cameras").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"CAM-98\", \"nome\": \"C\", \"estacaoId\": \"EST-98\", "
                        + "\"streamUrl\": \"http://127.0.0.1:8298/stream\", \"fonte\": \"f\"}"));
        String token = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/v1/stations/EST-98/token/regenerate").session(gestor))
                        .andReturn().getResponse().getContentAsString(),
                "$.token");
        MockHttpSession operador = loginOperador(mvc, token);
        String chave = cameras.findById("acme-test:CAM-98").orElseThrow().getApiKey();

        mvc.perform(get("/api/v1/incidents").session(operador).header(HEADER, chave))
                .andExpect(status().isForbidden());
    }

    @Test
    void cameraAdminItemExigeGestorNaoOperador() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);
        mvc.perform(post("/api/v1/stations").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-97\", \"nome\": \"A\", \"setor\": \"s\"}"));
        mvc.perform(post("/api/v1/cameras").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"CAM-97\", \"nome\": \"C\", \"estacaoId\": \"EST-97\", "
                        + "\"streamUrl\": \"http://127.0.0.1:8297/stream\", \"fonte\": \"f\"}"));
        String token = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/v1/stations/EST-97/token/regenerate").session(gestor))
                        .andReturn().getResponse().getContentAsString(),
                "$.token");
        MockHttpSession operador = loginOperador(mvc, token);

        mvc.perform(get("/api/v1/cameras/CAM-97").session(gestor)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/cameras/CAM-97").session(operador)).andExpect(status().isForbidden());
    }

    @Test
    void regenerarApiKeyDeCameraExigeGestorNaoOperador() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);
        mvc.perform(post("/api/v1/stations").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-94\", \"nome\": \"A\", \"setor\": \"s\"}"));
        mvc.perform(post("/api/v1/cameras").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"CAM-94\", \"nome\": \"C\", \"estacaoId\": \"EST-94\", "
                        + "\"streamUrl\": \"http://127.0.0.1:8294/stream\", \"fonte\": \"f\"}"));
        String token = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/v1/stations/EST-94/token/regenerate").session(gestor))
                        .andReturn().getResponse().getContentAsString(),
                "$.token");
        MockHttpSession operador = loginOperador(mvc, token);

        mvc.perform(post("/api/v1/cameras/CAM-94/apikey/regenerate").session(gestor))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/cameras/CAM-94/apikey/regenerate").session(operador))
                .andExpect(status().isForbidden());
    }

    @Test
    void zonasDeCameraExigeGestorNaoOperador() throws Exception {
        // CRUD de zona de risco não tem linha explícita em SecurityConfig —
        // cai no catch-all anyRequest().hasRole("GESTOR"). Prova aqui que
        // uma sessão OPERADOR válida (mesma câmera/estação dela) devolve
        // 403 em GET/POST/PUT/DELETE, análogo a
        // cameraAdminItemExigeGestorNaoOperador acima.
        MockHttpSession gestor = loginGestorBootstrap(mvc);
        mvc.perform(post("/api/v1/stations").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-ZR1\", \"nome\": \"A\", \"setor\": \"s\"}"));
        mvc.perform(post("/api/v1/cameras").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"CAM-ZRSEC\", \"nome\": \"C\", \"estacaoId\": \"EST-ZR1\", "
                        + "\"streamUrl\": \"http://127.0.0.1:8321/stream\", \"fonte\": \"f\"}"));
        String token = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/v1/stations/EST-ZR1/token/regenerate").session(gestor))
                        .andReturn().getResponse().getContentAsString(),
                "$.token");
        MockHttpSession operador = loginOperador(mvc, token);

        String poligono = "[{\"x\":0.2,\"y\":0.2},{\"x\":0.6,\"y\":0.2},{\"x\":0.6,\"y\":0.6}]";
        String corpoZona = mvc.perform(post("/api/v1/cameras/CAM-ZRSEC/zones").session(gestor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"x\", \"zoneType\": \"RESTRICTED\", "
                                + "\"polygon\": %s, \"severity\": 2, \"requiredPpe\": []}").formatted(poligono)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = com.jayway.jsonpath.JsonPath.read(corpoZona, "$.id");

        mvc.perform(get("/api/v1/cameras/CAM-ZRSEC/zones").session(operador))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/cameras/CAM-ZRSEC/zones").session(operador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"y\", \"zoneType\": \"RESTRICTED\", "
                                + "\"polygon\": %s, \"severity\": 2, \"requiredPpe\": []}").formatted(poligono)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/cameras/CAM-ZRSEC/zones/" + id).session(operador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"y\", \"zoneType\": \"RESTRICTED\", "
                                + "\"polygon\": %s, \"severity\": 2, \"requiredPpe\": []}").formatted(poligono)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/cameras/CAM-ZRSEC/zones/" + id).session(operador))
                .andExpect(status().isForbidden());
    }

    @Test
    void zonasConfigExigeGestorOuPipelineNaoOperador() throws Exception {
        // .../zones/config é hasAnyRole("PIPELINE","GESTOR") em
        // SecurityConfig — uma sessão OPERADOR (que não carrega nenhum dos
        // dois papéis) também deve cair fora, mesmo sendo a mesma câmera.
        MockHttpSession gestor = loginGestorBootstrap(mvc);
        mvc.perform(post("/api/v1/stations").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-ZR2\", \"nome\": \"A\", \"setor\": \"s\"}"));
        mvc.perform(post("/api/v1/cameras").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"CAM-ZRSEC2\", \"nome\": \"C\", \"estacaoId\": \"EST-ZR2\", "
                        + "\"streamUrl\": \"http://127.0.0.1:8322/stream\", \"fonte\": \"f\"}"));
        String token = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/v1/stations/EST-ZR2/token/regenerate").session(gestor))
                        .andReturn().getResponse().getContentAsString(),
                "$.token");
        MockHttpSession operador = loginOperador(mvc, token);

        mvc.perform(get("/api/v1/cameras/CAM-ZRSEC2/zones/config").session(operador))
                .andExpect(status().isForbidden());
    }

    @Test
    void zonasConfigSemChaveNemSessaoDevolve401() throws Exception {
        // Espelha cameraConfigSemChaveNemSessaoDevolve401 abaixo para a
        // rota irmã .../zones/config: sem sessão e sem X-Cypher-Key, deve
        // ser 401 (anônimo), não 403.
        mvc.perform(get("/api/v1/cameras/CAM-99/zones/config")).andExpect(status().isUnauthorized());
    }

    @Test
    void cameraConfigSemChaveNemSessaoDevolve401() throws Exception {
        // A propria chave nao tem superficie de leitura (Tarefa 6) — este teste
        // só confirma que, sem sessão E sem X-Cypher-Key, a rota devolve 401;
        // a Tarefa 13 é quem decide o que acontece SEM nenhuma chave nos
        // endpoints de ingestão, não aqui.
        mvc.perform(get("/api/v1/cameras/CAM-99/config")).andExpect(status().isUnauthorized());
    }

    @Test
    void ingestaoSemChaveDevolve401AposOFlipFinal() throws Exception {
        mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "incidentKey": "CAM-01:run-lock:track-1:20260810T160000000Z",
                  "cameraId": "CAM-01", "runId": "run-lock", "trackId": 1,
                  "type": "FALL_RISK", "severity": 4, "reason": "r",
                  "postureCategory": "immediate_risk", "rebaScore": 11, "actionLevel": 4,
                  "zoneName": null, "zoneStatus": "outside", "epiPresent": [], "epiMissing": [],
                  "detectedAt": "2026-08-10T16:00:00Z", "snapshotUrl": null
                }
                """)).andExpect(status().isUnauthorized());
    }
}
