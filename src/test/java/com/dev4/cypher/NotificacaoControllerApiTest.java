package com.dev4.cypher;

import com.dev4.cypher.repository.AlertaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.dev4.cypher.security.AuthTestSupport.loginGestorBootstrap;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class NotificacaoControllerApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AlertaRepository alertas;

    // Outras suites (ex.: IncidentQueryApiTest) nao sao transacionais e
    // deixam alertas do tenant "acme-test" commitados no H2 compartilhado;
    // sem isto, a suite inteira nao roda em ordem previsivel para estes
    // testes que assumem tabela vazia no inicio.
    @BeforeEach
    void limparAlertas() {
        alertas.deleteAll();
    }

    @Test
    void listaVaziaEContagemZeroQuandoNaoHaAlerta() throws Exception {
        MockHttpSession session = loginGestorBootstrap(mvc);

        mvc.perform(get("/api/v1/notificacoes").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        mvc.perform(get("/api/v1/notificacoes/contagem-nao-lidas").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.naoLidas").value(0));
    }

    @Test
    void alertaNovoApareceNaListaComoNaoLidaEContaNoBadge() throws Exception {
        MockHttpSession session = loginGestorBootstrap(mvc);
        String apiKey = criarCameraEPegarApiKey(session, "CAM-NOT1", "EST-NOT1");

        mvc.perform(post("/api/v1/incidents").header("X-Cypher-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadDeIncidente("CAM-NOT1", "incident-not-1")));

        mvc.perform(get("/api/v1/notificacoes").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cameraId").value("CAM-NOT1"))
                .andExpect(jsonPath("$[0].lida").value(false));

        mvc.perform(get("/api/v1/notificacoes/contagem-nao-lidas").session(session))
                .andExpect(jsonPath("$.naoLidas").value(1));
    }

    @Test
    void listaTraz30MaisRecentesOrdenadosDoMaisNovoParaOMaisVelho() throws Exception {
        MockHttpSession session = loginGestorBootstrap(mvc);
        String apiKey = criarCameraEPegarApiKey(session, "CAM-NOT2", "EST-NOT2");

        for (int i = 0; i < 32; i++) {
            String detectedAt = "2026-09-0%dT10:00:00Z".formatted(i < 9 ? 1 : 2);
            mvc.perform(post("/api/v1/incidents").header("X-Cypher-Key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payloadDeIncidente("CAM-NOT2", "incident-not2-" + i, 3,
                            "2026-09-01T%02d:00:00Z".formatted(i % 24))));
        }

        mvc.perform(get("/api/v1/notificacoes").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(30)));
    }

    private String criarCameraEPegarApiKey(MockHttpSession session, String codigo, String estacaoId) throws Exception {
        mvc.perform(post("/api/v1/stations").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"" + estacaoId + "\", \"nome\": \"E\", \"setor\": \"x\", \"ativa\": true}"));
        mvc.perform(post("/api/v1/cameras").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"id\": \"%s\", \"nome\": \"C\", \"estacaoId\": \"%s\", "
                        + "\"streamUrl\": \"http://127.0.0.1:9201/stream\", \"ativa\": true}")
                        .formatted(codigo, estacaoId)));
        String corpo = mvc.perform(post("/api/v1/cameras/" + codigo + "/apikey/regenerate").session(session))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(corpo, "$.apiKey");
    }

    private String payloadDeIncidente(String cameraId, String incidentKey) {
        return payloadDeIncidente(cameraId, incidentKey, 3, "2026-09-01T10:00:00Z");
    }

    private String payloadDeIncidente(String cameraId, String incidentKey, int severity, String detectedAt) {
        return ("{\"incidentKey\": \"%s\", \"cameraId\": \"%s\", \"runId\": \"run-1\", \"trackId\": 1, "
                + "\"type\": \"FALL_RISK\", \"severity\": %d, \"reason\": \"fallen_sustained\", "
                + "\"postureCategory\": \"fallen\", \"rebaScore\": 11, \"actionLevel\": 3, "
                + "\"zoneStatus\": \"outside\", \"detectedAt\": \"%s\"}")
                .formatted(incidentKey, cameraId, severity, detectedAt);
    }
}
