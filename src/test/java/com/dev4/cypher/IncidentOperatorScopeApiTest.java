package com.dev4.cypher;

import com.dev4.cypher.repository.AlertaRepository;
import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.repository.EstacaoRepository;
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
import static com.dev4.cypher.security.AuthTestSupport.loginOperador;
import static com.dev4.cypher.security.CypherKeyAuthenticationFilter.HEADER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class IncidentOperatorScopeApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EstacaoRepository estacoes;

    @Autowired
    private AlertaRepository alertas;

    @Autowired
    private CameraRepository cameras;

    private MockHttpSession gestor;
    private MockHttpSession operadorEst01;
    private Long idIncidenteEst01;
    private Long idIncidenteEst02;
    private Long idIncidenteCameraDesconhecida;

    @BeforeEach
    void setUp() throws Exception {
        alertas.deleteAll();
        gestor = loginGestorBootstrap(mvc);

        mvc.perform(post("/api/v1/stations").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-91\", \"nome\": \"A\", \"setor\": \"s\"}"));
        mvc.perform(post("/api/v1/stations").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-92\", \"nome\": \"B\", \"setor\": \"s\"}"));
        mvc.perform(post("/api/v1/cameras").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"CAM-91\", \"nome\": \"C1\", \"estacaoId\": \"EST-91\", "
                        + "\"streamUrl\": \"http://127.0.0.1:8291/stream\", \"fonte\": \"f\"}"));
        mvc.perform(post("/api/v1/cameras").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"CAM-92\", \"nome\": \"C2\", \"estacaoId\": \"EST-92\", "
                        + "\"streamUrl\": \"http://127.0.0.1:8292/stream\", \"fonte\": \"f\"}"));

        String chave = cameras.findById("CAM-91").orElseThrow().getApiKey();

        String r1 = mvc.perform(post("/api/v1/incidents").header(HEADER, chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incidente("op1", "CAM-91")))
                .andReturn().getResponse().getContentAsString();
        idIncidenteEst01 = ((Number) com.jayway.jsonpath.JsonPath.read(r1, "$.id")).longValue();

        String r2 = mvc.perform(post("/api/v1/incidents").header(HEADER, chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incidente("op2", "CAM-92")))
                .andReturn().getResponse().getContentAsString();
        idIncidenteEst02 = ((Number) com.jayway.jsonpath.JsonPath.read(r2, "$.id")).longValue();

        String r3 = mvc.perform(post("/api/v1/incidents").header(HEADER, chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incidente("op3", "CAM-DESCONHECIDA")))
                .andReturn().getResponse().getContentAsString();
        idIncidenteCameraDesconhecida = ((Number) com.jayway.jsonpath.JsonPath.read(r3, "$.id")).longValue();

        String token = estacoes.findById("EST-91").orElseThrow().getToken();
        operadorEst01 = loginOperador(mvc, token);
    }

    private String incidente(String key, String cameraId) {
        return """
                {
                  "incidentKey": "%s", "cameraId": "%s", "runId": "run-op", "trackId": 1,
                  "type": "FALL_RISK", "severity": 4, "reason": "r",
                  "postureCategory": "immediate_risk", "rebaScore": 11, "actionLevel": 4,
                  "zoneName": null, "zoneStatus": "outside", "epiPresent": [], "epiMissing": [],
                  "detectedAt": "2026-08-10T14:31:02.482-03:00", "snapshotUrl": null
                }
                """.formatted(key, cameraId);
    }

    @Test
    void operadorListaSoOsIncidentesDaPropriaEstacao() throws Exception {
        mvc.perform(get("/api/v1/incidents").session(operadorEst01))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].cameraId").value("CAM-91"));
    }

    @Test
    void gestorListaTodosOsIncidentesSemFiltro() throws Exception {
        mvc.perform(get("/api/v1/incidents").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void operadorLeIncidenteDaPropriaEstacao() throws Exception {
        mvc.perform(get("/api/v1/incidents/{id}", idIncidenteEst01).session(operadorEst01))
                .andExpect(status().isOk());
    }

    @Test
    void operadorRecebe403AoLerIncidenteDeOutraEstacao() throws Exception {
        mvc.perform(get("/api/v1/incidents/{id}", idIncidenteEst02).session(operadorEst01))
                .andExpect(status().isForbidden());
    }

    @Test
    void operadorMudaStatusDeIncidenteDaPropriaEstacao() throws Exception {
        mvc.perform(put("/api/v1/incidents/{id}/status", idIncidenteEst01).session(operadorEst01)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"REVISADO\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void operadorRecebe403AoMudarStatusDeIncidenteDeOutraEstacao() throws Exception {
        mvc.perform(put("/api/v1/incidents/{id}/status", idIncidenteEst02).session(operadorEst01)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"REVISADO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void operadorRecebe403AoLerIncidenteDeCameraDesconhecida() throws Exception {
        mvc.perform(get("/api/v1/incidents/{id}", idIncidenteCameraDesconhecida).session(operadorEst01))
                .andExpect(status().isForbidden());
    }

    @Test
    void operadorRecebe403AoMudarStatusDeIncidenteDeCameraDesconhecida() throws Exception {
        mvc.perform(put("/api/v1/incidents/{id}/status", idIncidenteCameraDesconhecida).session(operadorEst01)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"REVISADO\"}"))
                .andExpect(status().isForbidden());
    }
}
