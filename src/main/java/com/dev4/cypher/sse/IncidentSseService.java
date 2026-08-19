package com.dev4.cypher.sse;

import com.dev4.cypher.dto.IncidentResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registro em memória das conexões SSE abertas.
 *
 * <p>Estado volátil por natureza: reiniciar a aplicação derruba as conexões,
 * não os dados. O broadcast é por instância — adequado ao deploy de instância
 * única previsto para esta entrega.
 */
@Service
public class IncidentSseService {

    /** Sem timeout: quem decide encerrar é o cliente ou o heartbeat. */
    private static final long SEM_TIMEOUT = 0L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        return register(new SseEmitter(SEM_TIMEOUT));
    }

    /**
     * Registra um emitter já construído. Sem a remoção nos três callbacks, a
     * lista vaza uma entrada a cada refresh do browser.
     */
    public SseEmitter register(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    public void broadcast(String eventName, IncidentResponse incident) {
        enviarATodos(SseEmitter.event().name(eventName).data(incident));
    }

    /** Mantém a conexão viva através de proxies e detecta cliente morto. */
    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        enviarATodos(SseEmitter.event().comment("keep-alive"));
    }

    public int activeConnections() {
        return emitters.size();
    }

    private void enviarATodos(SseEmitter.SseEventBuilder evento) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(evento);
            } catch (IOException | IllegalStateException desconectado) {
                emitters.remove(emitter);
            }
        }
    }
}
