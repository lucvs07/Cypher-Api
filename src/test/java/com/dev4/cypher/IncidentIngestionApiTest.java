package com.dev4.cypher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IncidentIngestionApiTest {

    @Autowired
    private MockMvc mvc;

    private static final String QUEDA = """
            {
              "incidentKey": "CAM-01:run-api:track-7:20260810T143102482Z",
              "cameraId": "CAM-01",
              "runId": "run-api",
              "trackId": 7,
              "type": "FALL_RISK",
              "severity": 4,
              "reason": "fallen_sustained",
              "postureCategory": "immediate_risk",
              "rebaScore": 11,
              "actionLevel": 4,
              "zoneName": null,
              "zoneStatus": "outside",
              "epiPresent": ["HELMET", "VEST"],
              "epiMissing": [],
              "detectedAt": "2026-08-10T14:31:02.482-03:00",
              "snapshotUrl": null
            }
            """;

    @Test
    void criaIncidenteEDevolve201() throws Exception {
        mvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUEDA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.type").value("FALL_RISK"))
                .andExpect(jsonPath("$.nivel").value("ALTO"))
                .andExpect(jsonPath("$.severity").value(4))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.zoneName").doesNotExist());
    }

    @Test
    void ingestaoRepetidaDevolve200ComOMesmoId() throws Exception {
        String corpo = QUEDA.replace("run-api", "run-retry");

        String primeiro = mvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Tipar o id num local é necessário: JsonPath.read é genérico, e passá-lo
        // direto faz o compilador escolher value(Matcher) em vez de value(Object).
        Integer idOriginal = com.jayway.jsonpath.JsonPath.read(primeiro, "$.id");

        mvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idOriginal));
    }

    @Test
    void rejeitaSeveridadeForaDaFaixa() throws Exception {
        mvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUEDA.replace("\"severity\": 4", "\"severity\": 9")
                                .replace("run-api", "run-invalido")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejeitaPayloadSemChaveDeIdempotencia() throws Exception {
        mvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUEDA.replace(
                                "\"incidentKey\": \"CAM-01:run-api:track-7:20260810T143102482Z\"",
                                "\"incidentKey\": \"\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fechaIncidenteComDuracaoEPico() throws Exception {
        String corpo = QUEDA.replace("run-api", "run-close");
        mvc.perform(post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON).content(corpo));

        mvc.perform(patch("/api/v1/incidents/CAM-01:run-close:track-7:20260810T143102482Z")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "endedAt": "2026-08-10T14:31:09.140-03:00",
                                  "durationSeconds": 6.6,
                                  "peakSeverity": 4,
                                  "closedReason": "STATE_EXIT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationSeconds").value(6.6))
                .andExpect(jsonPath("$.closedReason").value("STATE_EXIT"));
    }

    @Test
    void fechamentoDeChaveInexistenteDevolve404() throws Exception {
        mvc.perform(patch("/api/v1/incidents/nao-existe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationSeconds\": 1.0}"))
                .andExpect(status().isNotFound());
    }
}
