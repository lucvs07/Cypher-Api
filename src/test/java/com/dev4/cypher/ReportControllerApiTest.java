package com.dev4.cypher;

import com.dev4.cypher.security.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class ReportControllerApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void csvDeIncidentesTemCabecalhoMesmoSemDado() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);

        String csv = mvc.perform(get("/api/v1/reports/incidents.csv")
                        .param("camera", "CAM-QUE-NAO-EXISTE-NESTE-TESTE").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).startsWith("id,incidentKey,cameraId,type,severity,nivel,reason,zoneName,detectedAt,endedAt,durationSeconds,status");
        // So o cabecalho: nenhuma linha de dado para uma camera que nao existe.
        assertThat(csv.trim().split("\r\n")).hasSize(1);
    }

    @Test
    void csvDeIncidentesContemUmaLinhaPorIncidenteDaCamera() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        String apiKey = criarCameraEPegarApiKey(session, "CAM-RPT1", "EST-01");

        mvc.perform(post("/api/v1/incidents").header("X-Cypher-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadDeIncidente("CAM-RPT1", "incident-rpt-1")));

        String csv = mvc.perform(get("/api/v1/reports/incidents.csv")
                        .param("camera", "CAM-RPT1").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv.trim().split("\r\n")).hasSize(2); // cabecalho + 1 linha
        assertThat(csv).contains("CAM-RPT1");
    }

    private String criarCameraEPegarApiKey(MockHttpSession session, String codigo, String estacaoId) throws Exception {
        mvc.perform(post("/api/v1/stations").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"" + estacaoId + "\", \"nome\": \"E\", \"setor\": \"x\", \"ativa\": true}"));
        mvc.perform(post("/api/v1/cameras").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"id\": \"%s\", \"nome\": \"C\", \"estacaoId\": \"%s\", "
                        + "\"streamUrl\": \"http://127.0.0.1:9200/stream\", \"ativa\": true}")
                        .formatted(codigo, estacaoId)));
        String corpo = mvc.perform(post("/api/v1/cameras/" + codigo + "/apikey/regenerate").session(session))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(corpo, "$.apiKey");
    }

    private String payloadDeIncidente(String cameraId, String incidentKey) {
        return ("{\"incidentKey\": \"%s\", \"cameraId\": \"%s\", \"runId\": \"run-1\", \"trackId\": 1, "
                + "\"type\": \"FALL_RISK\", \"severity\": 3, \"reason\": \"fallen_sustained\", "
                + "\"postureCategory\": \"fallen\", \"rebaScore\": 11, \"actionLevel\": 3, "
                + "\"zoneStatus\": \"outside\", \"detectedAt\": \"2026-08-29T10:00:00Z\"}")
                .formatted(incidentKey, cameraId);
    }
}
