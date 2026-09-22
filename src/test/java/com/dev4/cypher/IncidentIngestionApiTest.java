package com.dev4.cypher;

import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.model.Camera;
import com.dev4.cypher.repository.AlertaRepository;
import com.dev4.cypher.repository.CameraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.dev4.cypher.security.CypherKeyAuthenticationFilter.HEADER;
import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private AlertaRepository alertas;

    private static final String CHAVE = "chave-ingestao-teste";

    private static final String QUEDA = """
            {
              "incidentKey": "CAM-INGESTAO-TESTE:run-api:track-7:20260810T143102482Z",
              "cameraId": "CAM-INGESTAO-TESTE",
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
     * de quem rodou antes na mesma suíte. A câmera cadastrada é a MESMA
     * "CAM-INGESTAO-TESTE" usada no corpo de {@code QUEDA} — desde o achado
     * de segurança da revisão final, a chave só autentica para agir sobre a
     * PRÓPRIA câmera, então o id cadastrado e o {@code cameraId} do corpo
     * precisam bater.
     */
    @BeforeEach
    void garantirCameraDeIngestao() {
        if (cameras.findById("demo:CAM-INGESTAO-TESTE").isEmpty()) {
            cameras.saveAndFlush(new Camera("demo:CAM-INGESTAO-TESTE", "Camera de teste", "demo:EST-INGESTAO-TESTE",
                    "http://127.0.0.1:8999/stream", "f", true, CHAVE, "demo"));
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

    /**
     * Fix Round 1 — regressão do bypass CRITICAL encontrado na revisão da
     * Tarefa 13: "/api/v1/incident%73" é "/api/v1/incidents" com o 's' final
     * percent-encoded. O Spring MVC decodifica e roteia para o MESMO
     * handler de criação de incidente (prova de ponta a ponta, contexto
     * Spring real) — mas o filtro antigo comparava contra
     * request.getRequestURI() bruto e não reconhecia isso como rota de
     * ingestão, deixando passar sem exigir X-Cypher-Key. Usa
     * MockMvcRequestBuilders.request(...) com um java.net.URI construído à
     * mão: o builder de conveniência `post(...)` reencodaria '%' para '%25'
     * e mascararia o teste.
     */
    @Test
    void ingestaoComPathPercentEncodedSemChaveDevolve401() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .request(org.springframework.http.HttpMethod.POST,
                                java.net.URI.create("/api/v1/incident%73"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUEDA.replace("run-api", "run-percent-encoded")))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Confirma a outra metade do achado: o mesmo path percent-encoded, COM
     * a chave, é roteado pelo Spring MVC para o mesmo handler de criação
     * (201) — prova de que "/api/v1/incident%73" e "/api/v1/incidents" são
     * de fato a mesma rota de ingestão aos olhos do dispatcher, não só do
     * filtro.
     */
    @Test
    void ingestaoComPathPercentEncodedEChaveValidaCria201() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .request(org.springframework.http.HttpMethod.POST,
                                java.net.URI.create("/api/v1/incident%73"))
                        .header(HEADER, CHAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUEDA.replace("run-api", "run-percent-encoded-valido")))
                .andExpect(status().isCreated());
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
                                "\"incidentKey\": \"CAM-INGESTAO-TESTE:run-api:track-7:20260810T143102482Z\"",
                                "\"incidentKey\": \"\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fechaIncidenteComDuracaoEPico() throws Exception {
        String corpo = QUEDA.replace("run-api", "run-close");
        mvc.perform(post("/api/v1/incidents").header(HEADER, CHAVE)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));

        mvc.perform(patch("/api/v1/incidents/CAM-INGESTAO-TESTE:run-close:track-7:20260810T143102482Z")
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
    void fechaIncidenteReabertoPorRenotificacao() throws Exception {
        // cypher-capture fecha com RENOTIFIED quando a violacao persiste alem de
        // renotify_after_seconds; antes do enum conhecer o valor, este PATCH era 400.
        String corpo = QUEDA.replace("run-api", "run-renotify");
        mvc.perform(post("/api/v1/incidents").header(HEADER, CHAVE)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));

        mvc.perform(patch("/api/v1/incidents/CAM-INGESTAO-TESTE:run-renotify:track-7:20260810T143102482Z")
                        .header(HEADER, CHAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "endedAt": "2026-08-10T14:33:02.482-03:00",
                                  "durationSeconds": 120.0,
                                  "closedReason": "RENOTIFIED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedReason").value("RENOTIFIED"));
    }

    @Test
    void fechamentoSemChaveDevolve401() throws Exception {
        mvc.perform(patch("/api/v1/incidents/nao-importa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationSeconds\": 1.0}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void incidenteIngeridoGuardaChaveECameraIdPrefixadosPeloTenantDaChave() throws Exception {
        // CAM-INGESTAO-TESTE (semeada no @BeforeEach desta classe) pertence ao tenant "demo".
        mvc.perform(post("/api/v1/incidents").header(HEADER, CHAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUEDA.replace("run-api", "incident-key-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cameraId").value("CAM-INGESTAO-TESTE"));

        Alerta salvo = alertas.findByIncidentKey(
                "demo:CAM-INGESTAO-TESTE:incident-key-1:track-7:20260810T143102482Z").orElseThrow();
        assertThat(salvo.getCameraId()).isEqualTo("demo:CAM-INGESTAO-TESTE");
        assertThat(salvo.getEmpresaId()).isEqualTo("demo");
    }

    @Test
    void fechamentoDeChaveInexistenteDevolve404() throws Exception {
        mvc.perform(patch("/api/v1/incidents/nao-existe").header(HEADER, CHAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationSeconds\": 1.0}"))
                .andExpect(status().isNotFound());
    }
}
