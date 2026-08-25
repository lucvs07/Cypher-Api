package com.dev4.cypher;

import com.dev4.cypher.model.Camera;
import com.dev4.cypher.repository.CameraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.dev4.cypher.security.CypherKeyAuthenticationFilter.HEADER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IncidentIngestionApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CameraRepository cameras;

    private static final String CHAVE = "chave-ingestao-teste";

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

    /**
     * Upsert, nunca `deleteAll`: outras classes de teste do contexto padrão
     * também escrevem câmera/estação — apagar tudo aqui derrubaria fixture
     * de quem rodou antes na mesma suíte. A chave é reaproveitada por
     * qualquer câmera cadastrada (o filtro não compara com `cameraId` do
     * corpo — spec E4 §2.3).
     */
    @BeforeEach
    void garantirCameraDeIngestao() {
        if (cameras.findById("CAM-INGESTAO-TESTE").isEmpty()) {
            cameras.saveAndFlush(new Camera("CAM-INGESTAO-TESTE", "Camera de teste", "EST-INGESTAO-TESTE",
                    "http://127.0.0.1:8999/stream", "f", true, CHAVE));
        }
    }

    @Test
    void criaIncidenteEDevolve201() throws Exception {
        mvc.perform(post("/api/v1/incidents").header(HEADER, CHAVE)
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
    void ingestaoSemChaveDevolve401() throws Exception {
        mvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUEDA.replace("run-api", "run-sem-chave")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingestaoComChaveInvalidaDevolve401() throws Exception {
        mvc.perform(post("/api/v1/incidents").header(HEADER, "chave-que-nao-existe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUEDA.replace("run-api", "run-chave-invalida")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingestaoRepetidaDevolve200ComOMesmoId() throws Exception {
        String corpo = QUEDA.replace("run-api", "run-retry");

        String primeiro = mvc.perform(post("/api/v1/incidents").header(HEADER, CHAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Integer idOriginal = com.jayway.jsonpath.JsonPath.read(primeiro, "$.id");

        mvc.perform(post("/api/v1/incidents").header(HEADER, CHAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idOriginal));
    }

    @Test
    void rejeitaSeveridadeForaDaFaixa() throws Exception {
        mvc.perform(post("/api/v1/incidents").header(HEADER, CHAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUEDA.replace("\"severity\": 4", "\"severity\": 9")
                                .replace("run-api", "run-invalido")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejeitaPayloadSemChaveDeIdempotencia() throws Exception {
        mvc.perform(post("/api/v1/incidents").header(HEADER, CHAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUEDA.replace(
                                "\"incidentKey\": \"CAM-01:run-api:track-7:20260810T143102482Z\"",
                                "\"incidentKey\": \"\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fechaIncidenteComDuracaoEPico() throws Exception {
        String corpo = QUEDA.replace("run-api", "run-close");
        mvc.perform(post("/api/v1/incidents").header(HEADER, CHAVE)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));

        mvc.perform(patch("/api/v1/incidents/CAM-01:run-close:track-7:20260810T143102482Z")
                        .header(HEADER, CHAVE)
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
    void fechamentoSemChaveDevolve401() throws Exception {
        mvc.perform(patch("/api/v1/incidents/nao-importa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationSeconds\": 1.0}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fechamentoDeChaveInexistenteDevolve404() throws Exception {
        mvc.perform(patch("/api/v1/incidents/nao-existe").header(HEADER, CHAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationSeconds\": 1.0}"))
                .andExpect(status().isNotFound());
    }
}
