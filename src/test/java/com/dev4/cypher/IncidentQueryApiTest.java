package com.dev4.cypher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.dev4.cypher.repository.AlertaRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IncidentQueryApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AlertaRepository repository;

    private String incidente(String key, String camera, int severidade,
                             String tipo, String instante) {
        return """
                {
                  "incidentKey": "%s",
                  "cameraId": "%s",
                  "runId": "run-q",
                  "trackId": 1,
                  "type": "%s",
                  "severity": %d,
                  "reason": "regra",
                  "postureCategory": "immediate_risk",
                  "rebaScore": 9,
                  "actionLevel": 3,
                  "zoneName": null,
                  "zoneStatus": "outside",
                  "epiPresent": [],
                  "epiMissing": [],
                  "detectedAt": "%s",
                  "snapshotUrl": null
                }
                """.formatted(key, camera, tipo, severidade, instante);
    }

    @BeforeEach
    void semear() throws Exception {
        repository.deleteAll();
        mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON)
                .content(incidente("q1", "CAM-01", 4, "FALL_RISK", "2026-08-10T08:00:00-03:00")));
        mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON)
                .content(incidente("q2", "CAM-02", 2, "ERGONOMIC_RISK", "2026-08-10T09:00:00-03:00")));
        mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON)
                .content(incidente("q3", "CAM-01", 3, "ZONE_VIOLATION", "2026-08-10T10:00:00-03:00")));
    }

    @Test
    void listaMaisRecentesPrimeiro() throws Exception {
        mvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].incidentKey").value("q3"));
    }

    @Test
    void filtraPorCamera() throws Exception {
        mvc.perform(get("/api/v1/incidents").param("camera", "CAM-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtraPorSeveridadeMinima() throws Exception {
        mvc.perform(get("/api/v1/incidents").param("severityMin", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtraPorInstanteParaCobrirLacunaDoSse() throws Exception {
        mvc.perform(get("/api/v1/incidents").param("since", "2026-08-10T09:30:00-03:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].incidentKey").value("q3"));
    }

    @Test
    void pagina() throws Exception {
        mvc.perform(get("/api/v1/incidents").param("size", "2").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void operadorReconheceIncidente() throws Exception {
        String lista = mvc.perform(get("/api/v1/incidents").param("camera", "CAM-02"))
                .andReturn().getResponse().getContentAsString();
        Integer id = com.jayway.jsonpath.JsonPath.read(lista, "$.content[0].id");

        mvc.perform(put("/api/v1/incidents/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"REVISADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVISADO"));
    }

    @Test
    void statusDeIncidenteInexistenteDevolve404() throws Exception {
        mvc.perform(put("/api/v1/incidents/{id}/status", 999999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"RESOLVIDO\"}"))
                .andExpect(status().isNotFound());
    }
}
