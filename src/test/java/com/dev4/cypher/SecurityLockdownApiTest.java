package com.dev4.cypher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static com.dev4.cypher.security.AuthTestSupport.loginGestorBootstrap;
import static com.dev4.cypher.security.AuthTestSupport.loginOperador;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityLockdownApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void healthContinuaPublico() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }

    @Test
    void setupStatusContinuaPublico() throws Exception {
        mvc.perform(get("/api/v1/setup/status")).andExpect(status().isOk());
    }

    @Test
    void setupPosConfiguradoDevolve409NaoAutenticacao() throws Exception {
        // Garante que ao menos um Usuario já existe (o gestor bootstrap,
        // idempotente — ver AuthTestSupport). A prova aqui é que a resposta
        // é 409 (endpoint de execução única já fechado), não 401: POST
        // /setup continua público mesmo travado, nunca escondido atrás de
        // sessão/papel (spec E4 §2.5/§2.6).
        loginGestorBootstrap(mvc);

        mvc.perform(post("/api/v1/setup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"outro\", \"password\": \"x\"}"))
                .andExpect(status().isConflict());
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
    void camerasEStationsListaAceitamGestorEOperador() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);
        mvc.perform(post("/api/v1/stations").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-96\", \"nome\": \"A\", \"setor\": \"s\"}"));
        String token = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/v1/stations/EST-96/token/regenerate").session(gestor))
                        .andReturn().getResponse().getContentAsString(),
                "$.token");
        MockHttpSession operador = loginOperador(mvc, token);

        mvc.perform(get("/api/v1/cameras").session(operador)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/stations").session(operador)).andExpect(status().isOk());
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
    void cameraConfigSemChaveNemSessaoDevolve401() throws Exception {
        // A propria chave nao tem superficie de leitura (Tarefa 6) — este teste
        // só confirma que, sem sessão E sem X-Cypher-Key, a rota devolve 401;
        // a Tarefa 13 é quem decide o que acontece SEM nenhuma chave nos
        // endpoints de ingestão, não aqui.
        mvc.perform(get("/api/v1/cameras/CAM-99/config")).andExpect(status().isUnauthorized());
    }

    @Test
    void ingestaoContinuaAbertaSemChave() throws Exception {
        // Flip para obrigatorio e a Tarefa 13 — aqui a ingestao ainda aceita
        // sem X-Cypher-Key (spec E4 §5/§7).
        mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "incidentKey": "CAM-01:run-lock:track-1:20260810T160000000Z",
                  "cameraId": "CAM-01", "runId": "run-lock", "trackId": 1,
                  "type": "FALL_RISK", "severity": 4, "reason": "r",
                  "postureCategory": "immediate_risk", "rebaScore": 11, "actionLevel": 4,
                  "zoneName": null, "zoneStatus": "outside", "epiPresent": [], "epiMissing": [],
                  "detectedAt": "2026-08-10T16:00:00Z", "snapshotUrl": null
                }
                """)).andExpect(status().isCreated());
    }
}
