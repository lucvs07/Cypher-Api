package com.dev4.cypher.sse;

import com.dev4.cypher.dto.IncidentResponse;
import com.dev4.cypher.model.Camera;
import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.security.TenantContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registro em memória das conexões SSE abertas, escopado por empresa e, dentro
 * dela, opcionalmente por estação: uma inscrição sempre exige `empresaId` — só
 * recebe incidentes do próprio tenant; uma inscrição `OPERADOR` (estacaoId não
 * nula) ainda restringe às câmeras da própria estação, enquanto uma inscrição
 * irrestrita (GESTOR, estacaoId nula) recebe tudo dentro da empresa — mesma
 * política de 403 de `IncidentController`, mas expressa como filtro em vez de
 * rejeição (uma conexão SSE não tem por onde devolver 403 a meio do fluxo).
 *
 * <p>Estado volátil por natureza: reiniciar a aplicação derruba as conexões,
 * não os dados. O broadcast é por instância — adequado ao deploy de instância
 * única previsto para esta entrega.
 */
@Service
public class IncidentSseService {

    private static final long SEM_TIMEOUT = 0L;

    private record Subscription(SseEmitter emitter, String empresaId, String estacaoId) {
    }

    private final CameraRepository cameras;
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    public IncidentSseService(CameraRepository cameras) {
        this.cameras = cameras;
    }

    /** Irrestrito dentro da própria empresa — usado por GESTOR. */
    public SseEmitter subscribe(String empresaId) {
        return subscribe(empresaId, null);
    }

    /** @param estacaoId nulo = sem filtro de estação dentro da empresa (GESTOR); não-nulo = só incidentes dessa estação (OPERADOR). */
    public SseEmitter subscribe(String empresaId, String estacaoId) {
        return register(new SseEmitter(SEM_TIMEOUT), empresaId, estacaoId);
    }

    public SseEmitter register(SseEmitter emitter, String empresaId, String estacaoId) {
        Subscription subscription = new Subscription(emitter, empresaId, estacaoId);
        subscriptions.add(subscription);
        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> subscriptions.remove(subscription));
        emitter.onError(e -> subscriptions.remove(subscription));
        return emitter;
    }

    public void broadcast(String eventName, IncidentResponse incident) {
        String estacaoDoIncidente = cameras.findById(
                        TenantContext.key(incident.empresaId(), incident.cameraId()))
                .map(Camera::getEstacaoId)
                .map(TenantContext::codigo)
                .orElse(null);
        enviarAosQueEnxergam(incident.empresaId(), estacaoDoIncidente,
                SseEmitter.event().name(eventName).data(incident));
    }

    /** Mantém a conexão viva através de proxies e detecta cliente morto. */
    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        enviarATodos(SseEmitter.event().comment("keep-alive"));
    }

    public int activeConnections() {
        return subscriptions.size();
    }

    private void enviarAosQueEnxergam(String empresaDoIncidente, String estacaoDoIncidente,
                                      SseEmitter.SseEventBuilder evento) {
        for (Subscription subscription : subscriptions) {
            boolean mesmaEmpresa = Objects.equals(subscription.empresaId(), empresaDoIncidente);
            boolean enxergaEstacao = subscription.estacaoId() == null
                    || subscription.estacaoId().equals(estacaoDoIncidente);
            if (!mesmaEmpresa || !enxergaEstacao) {
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
