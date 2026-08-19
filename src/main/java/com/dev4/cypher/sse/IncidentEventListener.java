package com.dev4.cypher.sse;

import com.dev4.cypher.event.IncidentChangedEvent;
import com.dev4.cypher.event.IncidentCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ponte entre os eventos de domínio e o push.
 *
 * <p>AFTER_COMMIT é essencial: dentro da transação, um rollback deixaria o
 * operador vendo no painel um incidente que não existe no banco.
 */
@Component
public class IncidentEventListener {

    private final IncidentSseService sse;

    public IncidentEventListener(IncidentSseService sse) {
        this.sse = sse;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(IncidentCreatedEvent event) {
        sse.broadcast("incident", event.incident());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(IncidentChangedEvent event) {
        sse.broadcast("incident-changed", event.incident());
    }
}
