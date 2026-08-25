package com.dev4.cypher.sse;

import com.dev4.cypher.dto.IncidentResponse;
import com.dev4.cypher.repository.CameraRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registro em memória das conexões SSE abertas, com escopo opcional por
 * estação: uma inscrição `OPERADOR` (estacaoId não nula) só recebe
 * incidentes cuja câmera pertence à mesma estação; uma inscrição irrestrita
 * (GESTOR, estacaoId nula) recebe tudo — mesma política de 403 de
 * `IncidentController`, mas expressa como filtro em vez de rejeição (uma
 * conexão SSE não tem por onde devolver 403 a meio do fluxo).
 *
 * <p>Estado volátil por natureza: reiniciar a aplicação derruba as conexões,
 * não os dados. O broadcast é por instância — adequado ao deploy de instância
 * única previsto para esta entrega.
 */
@Service
public class IncidentSseService {

    private static final long SEM_TIMEOUT = 0L;

    private record Subscription(SseEmitter emitter, String estacaoId) {
    }

    private final CameraRepository cameras;
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    public IncidentSseService(CameraRepository cameras) {
        this.cameras = cameras;
    }

    /** Irrestrito — usado por GESTOR e por quem ainda não tem sessão. */
    public SseEmitter subscribe() {
        return subscribe(null);
    }

    /** @param estacaoId nulo = sem filtro (GESTOR); não-nulo = só incidentes dessa estação (OPERADOR). */
    public SseEmitter subscribe(String estacaoId) {
        return register(new SseEmitter(SEM_TIMEOUT), estacaoId);
    }

    public SseEmitter register(SseEmitter emitter, String estacaoId) {
        Subscription subscription = new Subscription(emitter, estacaoId);
        subscriptions.add(subscription);
        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> subscriptions.remove(subscription));
        emitter.onError(e -> subscriptions.remove(subscription));
        return emitter;
    }

    public void broadcast(String eventName, IncidentResponse incident) {
        String estacaoDoIncidente = cameras.findById(incident.cameraId())
                .map(c -> c.getEstacaoId())
                .orElse(null);
        enviarAosQueEnxergam(estacaoDoIncidente, SseEmitter.event().name(eventName).data(incident));
    }

    /** Mantém a conexão viva através de proxies e detecta cliente morto. */
    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        enviarATodos(SseEmitter.event().comment("keep-alive"));
    }

    public int activeConnections() {
        return subscriptions.size();
    }

    private void enviarAosQueEnxergam(String estacaoDoIncidente, SseEmitter.SseEventBuilder evento) {
        for (Subscription subscription : subscriptions) {
            boolean enxerga = subscription.estacaoId() == null
                    || subscription.estacaoId().equals(estacaoDoIncidente);
            if (!enxerga) {
                continue;
            }
            enviar(subscription, evento);
        }
    }

    private void enviarATodos(SseEmitter.SseEventBuilder evento) {
        for (Subscription subscription : subscriptions) {
            enviar(subscription, evento);
        }
    }

    private void enviar(Subscription subscription, SseEmitter.SseEventBuilder evento) {
        try {
            subscription.emitter().send(evento);
        } catch (IOException | IllegalStateException desconectado) {
            subscriptions.remove(subscription);
        }
    }
}
