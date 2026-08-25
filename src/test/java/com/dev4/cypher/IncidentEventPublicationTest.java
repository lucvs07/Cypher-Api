package com.dev4.cypher;

import com.dev4.cypher.event.IncidentCreatedEvent;
import com.dev4.cypher.model.Camera;
import com.dev4.cypher.repository.CameraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import static com.dev4.cypher.security.AuthTestSupport.loginGestorBootstrap;
import static com.dev4.cypher.security.CypherKeyAuthenticationFilter.HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(IncidentEventPublicationTest.Espiao.class)
class IncidentEventPublicationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Espiao espiao;

    @Autowired
    private CameraRepository cameras;

    @BeforeEach
    void garantirCameraDeIngestao() {
        if (cameras.findById("CAM-INGESTAO-TESTE").isEmpty()) {
            cameras.saveAndFlush(new Camera("CAM-INGESTAO-TESTE", "Camera de teste", "EST-INGESTAO-TESTE",
                    "http://127.0.0.1:8999/stream", "f", true, "chave-ingestao-teste"));
        }
    }

    /**
     * Registra o que chega depois do commit — nunca durante a transação.
     * A própria classe @TestConfiguration vira bean ao ser importada, então
     * o @Autowired acima a resolve sem método de fábrica.
     */
    @TestConfiguration
    static class Espiao {

        final List<String> recebidosAposCommit = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onCreated(IncidentCreatedEvent event) {
            recebidosAposCommit.add(event.incident().incidentKey());
        }
    }

    @Test
    void publicaEventoSomenteDepoisDoCommit() throws Exception {
        String corpo = """
                {
                  "incidentKey": "CAM-01:run-evt:track-1:20260810T150000000Z",
                  "cameraId": "CAM-01",
                  "runId": "run-evt",
                  "trackId": 1,
                  "type": "FALL_RISK",
                  "severity": 4,
                  "reason": "fallen_sustained",
                  "postureCategory": "immediate_risk",
                  "rebaScore": 11,
                  "actionLevel": 4,
                  "zoneName": null,
                  "zoneStatus": "outside",
                  "epiPresent": [],
                  "epiMissing": [],
                  "detectedAt": "2026-08-10T15:00:00Z",
                  "snapshotUrl": null
                }
                """;

        mvc.perform(post("/api/v1/incidents").header(HEADER, "chave-ingestao-teste")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());

        assertThat(espiao.recebidosAposCommit)
                .contains("CAM-01:run-evt:track-1:20260810T150000000Z");
    }

    @Test
    void retryNaoRepublicaOEvento() throws Exception {
        String corpo = """
                {
                  "incidentKey": "CAM-01:run-retry-evt:track-1:20260810T150000000Z",
                  "cameraId": "CAM-01",
                  "runId": "run-retry-evt",
                  "trackId": 1,
                  "type": "FALL_RISK",
                  "severity": 4,
                  "reason": "fallen_sustained",
                  "postureCategory": "immediate_risk",
                  "rebaScore": 11,
                  "actionLevel": 4,
                  "zoneName": null,
                  "zoneStatus": "outside",
                  "epiPresent": [],
                  "epiMissing": [],
                  "detectedAt": "2026-08-10T15:00:00Z",
                  "snapshotUrl": null
                }
                """;

        mvc.perform(post("/api/v1/incidents").header(HEADER, "chave-ingestao-teste")
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
        mvc.perform(post("/api/v1/incidents").header(HEADER, "chave-ingestao-teste")
                .contentType(MediaType.APPLICATION_JSON).content(corpo));

        long publicacoes = espiao.recebidosAposCommit.stream()
                .filter(k -> k.contains("run-retry-evt"))
                .count();
        assertThat(publicacoes).isEqualTo(1);
    }

    @Test
    void streamAbreConexaoSse() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);

        mvc.perform(get("/api/v1/incidents/stream").session(gestor))
                .andExpect(status().isOk());
    }
}
