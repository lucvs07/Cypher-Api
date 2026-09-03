package com.dev4.cypher.webhook;

import com.dev4.cypher.dto.IncidentResponse;
import com.dev4.cypher.event.IncidentCreatedEvent;
import com.dev4.cypher.model.AlertaNivel;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.model.AlertaTipo;
import com.dev4.cypher.model.DeliveryStatus;
import com.dev4.cypher.model.WebhookDelivery;
import com.dev4.cypher.model.WebhookSubscription;
import com.dev4.cypher.repository.WebhookDeliveryRepository;
import com.dev4.cypher.repository.WebhookSubscriptionRepository;
import com.sun.net.httpserver.HttpServer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WebhookDispatcherTest {

    @Autowired
    private WebhookDispatcher dispatcher;

    @Autowired
    private WebhookSubscriptionRepository assinaturas;

    @Autowired
    private WebhookDeliveryRepository entregas;

    private HttpServer servidor;
    private final List<String> assinaturasRecebidas = new CopyOnWriteArrayList<>();
    private volatile int statusDeResposta = 200;

    @BeforeEach
    void subirAssinante() throws IOException {
        assinaturas.deleteAll();
        entregas.deleteAll();
        assinaturasRecebidas.clear();
        statusDeResposta = 200;

        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/hook", troca -> {
            assinaturasRecebidas.add(troca.getRequestHeaders()
                    .getFirst("X-Cypher-Signature"));
            troca.sendResponseHeaders(statusDeResposta, -1);
            troca.close();
        });
        servidor.start();

        assinaturas.save(new WebhookSubscription(null,
                "http://127.0.0.1:" + servidor.getAddress().getPort() + "/hook",
                "segredo", 3, true, "demo"));
    }

    @AfterEach
    void derrubarAssinante() {
        servidor.stop(0);
    }

    private IncidentResponse incidente(int severidade) {
        return incidente(severidade, "demo");
    }

    private IncidentResponse incidente(int severidade, String empresaId) {
        return new IncidentResponse(1L, "k-hook", "CAM-01", "run-a", 7,
                AlertaTipo.FALL_RISK, severidade, AlertaNivel.fromSeverity(severidade),
                "descricao", "fallen_sustained", "immediate_risk", 11, 4, null, "outside",
                List.of(), List.of(), null,
                OffsetDateTime.of(2026, 8, 10, 14, 31, 2, 0, ZoneOffset.UTC),
                null, null, null, null, AlertaStatus.PENDENTE, empresaId);
    }

    private IncidentResponse incidenteDeExemplo(String empresaId) {
        return incidente(4, empresaId);
    }

    @Test
    void entregaIncidenteAssinadoAoAssinante() {
        dispatcher.onIncidentCreated(new IncidentCreatedEvent(incidente(4)));

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(assinaturasRecebidas).hasSize(1));

        assertThat(assinaturasRecebidas.get(0)).startsWith("sha256=");
        assertThat(entregas.findByIncidentKey("k-hook"))
                .singleElement()
                .satisfies(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.ENTREGUE));
    }

    @Test
    void naoEntregaAbaixoDaSeveridadeMinima() {
        dispatcher.onIncidentCreated(new IncidentCreatedEvent(incidente(2)));

        assertThat(assinaturasRecebidas).isEmpty();
        assertThat(entregas.findByIncidentKey("k-hook")).isEmpty();
    }

    @Test
    void registraFalhaComoPendenteParaRetry() {
        statusDeResposta = 500;

        dispatcher.onIncidentCreated(new IncidentCreatedEvent(incidente(4)));

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(entregas.findByIncidentKey("k-hook"))
                        .singleElement()
                        .satisfies(d -> {
                            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.PENDENTE);
                            assertThat(d.getTentativas()).isEqualTo(1);
                            assertThat(d.getProximaTentativa()).isNotNull();
                        }));
    }

    @Test
    void varredorReenviaPendenteEMarcaEntregue() {
        statusDeResposta = 500;
        dispatcher.onIncidentCreated(new IncidentCreatedEvent(incidente(4)));
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(entregas.findByIncidentKey("k-hook")).hasSize(1));

        // Antecipa a próxima tentativa e volta o assinante ao normal.
        entregas.findByIncidentKey("k-hook").forEach(d -> {
            d.setProximaTentativa(OffsetDateTime.now().minusMinutes(1));
            entregas.save(d);
        });
        statusDeResposta = 200;

        dispatcher.reenviarPendentes();

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(entregas.findByIncidentKey("k-hook"))
                        .singleElement()
                        .satisfies(d -> assertThat(d.getStatus())
                                .isEqualTo(DeliveryStatus.ENTREGUE)));
    }

    @Test
    void assinanteDeUmaEmpresaNaoRecebeIncidenteDeOutra() {
        // Aponta as duas assinaturas para o mock local (não para um host real):
        // onIncidentCreated é @Async, então o teste precisa de Awaitility, e um
        // host inalcançável (ex.: https://exemplo.com) faria a tentativa demorar
        // dezenas de segundos por causa do timeout de conexão, abrindo uma janela
        // em que "só existe 1 delivery" seria verdade por motivo errado (a segunda
        // ainda não tinha sido tentada) — daí a asserção também checar a
        // identidade da assinatura, não só a contagem.
        String base = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/hook";
        WebhookSubscription acme = assinaturas.saveAndFlush(
                new WebhookSubscription(null, base + "?t=acme", "segredo", 0, true, "acme"));
        assinaturas.saveAndFlush(
                new WebhookSubscription(null, base + "?t=globex", "segredo", 0, true, "globex"));

        IncidentResponse incidenteAcme = incidenteDeExemplo("acme");
        dispatcher.onIncidentCreated(new IncidentCreatedEvent(incidenteAcme));

        // Só a delivery da assinatura "acme" deve existir — nenhuma para "globex".
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(entregas.findAll())
                        .singleElement()
                        .satisfies(d -> assertThat(d.getSubscriptionId()).isEqualTo(acme.getId())));

        // Garante que a contagem de 1 se mantém (não é um instantâneo de uma
        // corrida em andamento).
        Awaitility.await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(entregas.findAll()).hasSize(1));
    }
}
