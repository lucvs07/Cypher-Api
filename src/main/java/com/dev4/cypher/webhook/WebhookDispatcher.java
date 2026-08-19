package com.dev4.cypher.webhook;

import com.dev4.cypher.dto.IncidentResponse;
import com.dev4.cypher.event.IncidentCreatedEvent;
import com.dev4.cypher.model.DeliveryStatus;
import com.dev4.cypher.model.WebhookDelivery;
import com.dev4.cypher.model.WebhookSubscription;
import com.dev4.cypher.repository.WebhookDeliveryRepository;
import com.dev4.cypher.repository.WebhookSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Fan-out de incidentes para assinantes externos.
 *
 * <p>Assíncrono de propósito: uma falha de entrega registra a delivery como
 * pendente e não afeta a ingestão nem o dashboard. O varredor agendado
 * retenta com backoff exponencial.
 *
 * <p>O mapper é o {@code tools.jackson.databind.ObjectMapper} do Jackson 3 —
 * o pacote {@code com.fasterxml.jackson.databind} não existe mais nesta versão.
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    private static final int MAX_TENTATIVAS = 6;

    private final WebhookSubscriptionRepository assinaturas;
    private final WebhookDeliveryRepository entregas;
    private final WebhookSigner signer;
    private final ObjectMapper objectMapper;
    private final RestClient http = RestClient.create();

    public WebhookDispatcher(WebhookSubscriptionRepository assinaturas,
                             WebhookDeliveryRepository entregas,
                             WebhookSigner signer,
                             ObjectMapper objectMapper) {
        this.assinaturas = assinaturas;
        this.entregas = entregas;
        this.signer = signer;
        this.objectMapper = objectMapper;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentCreated(IncidentCreatedEvent event) {
        IncidentResponse incidente = event.incident();
        String payload = serializar(incidente);

        for (WebhookSubscription assinatura : assinaturas.findByAtivoTrue()) {
            if (incidente.severity() < assinatura.getMinSeverity()) {
                continue;
            }
            WebhookDelivery entrega = new WebhookDelivery(null, assinatura.getId(),
                    incidente.incidentKey(), payload, DeliveryStatus.PENDENTE, 0, null, null);
            tentarEntregar(entrega, assinatura);
        }
    }

    /** Retenta entregas pendentes cuja próxima tentativa já venceu. */
    @Scheduled(fixedRate = 30_000)
    public void reenviarPendentes() {
        List<WebhookDelivery> pendentes = entregas
                .findByStatusAndProximaTentativaBefore(DeliveryStatus.PENDENTE, OffsetDateTime.now());

        for (WebhookDelivery entrega : pendentes) {
            assinaturas.findById(entrega.getSubscriptionId())
                    .ifPresent(assinatura -> tentarEntregar(entrega, assinatura));
        }
    }

    private void tentarEntregar(WebhookDelivery entrega, WebhookSubscription assinatura) {
        String timestamp = String.valueOf(OffsetDateTime.now().toEpochSecond());
        entrega.setTentativas(entrega.getTentativas() + 1);

        try {
            http.post()
                    .uri(assinatura.getUrl())
                    .header("Content-Type", "application/json")
                    .header("X-Cypher-Timestamp", timestamp)
                    .header("X-Cypher-Signature",
                            signer.sign(assinatura.getSecret(), timestamp, entrega.getPayload()))
                    .body(entrega.getPayload())
                    .retrieve()
                    .toBodilessEntity();

            entrega.setStatus(DeliveryStatus.ENTREGUE);
            entrega.setUltimoErro(null);
            entrega.setProximaTentativa(null);
        } catch (Exception falha) {
            entrega.setUltimoErro(truncar(falha.getMessage()));
            if (entrega.getTentativas() >= MAX_TENTATIVAS) {
                entrega.setStatus(DeliveryStatus.FALHA);
                entrega.setProximaTentativa(null);
                log.warn("Webhook desistiu apos {} tentativas: incidente {} para {}",
                        entrega.getTentativas(), entrega.getIncidentKey(), assinatura.getUrl());
            } else {
                entrega.setStatus(DeliveryStatus.PENDENTE);
                // Backoff exponencial: 30s, 60s, 120s, 240s, 480s.
                long segundos = 30L * (1L << (entrega.getTentativas() - 1));
                entrega.setProximaTentativa(OffsetDateTime.now().plusSeconds(segundos));
            }
        }

        entregas.save(entrega);
    }

    private String serializar(IncidentResponse incidente) {
        try {
            return objectMapper.writeValueAsString(incidente);
        } catch (JacksonException e) {
            throw new IllegalStateException("Falha ao serializar incidente para webhook", e);
        }
    }

    private String truncar(String mensagem) {
        if (mensagem == null) {
            return "erro sem mensagem";
        }
        return mensagem.length() <= 500 ? mensagem : mensagem.substring(0, 500);
    }
}
