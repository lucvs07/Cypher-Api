package com.dev4.cypher.sse;

import com.dev4.cypher.dto.IncidentResponse;
import com.dev4.cypher.model.AlertaNivel;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.model.AlertaTipo;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentSseServiceTest {

    private final IncidentSseService service = new IncidentSseService();

    private IncidentResponse incidente() {
        return new IncidentResponse(1L, "k", "CAM-01", "run-a", 7,
                AlertaTipo.FALL_RISK, 4, AlertaNivel.ALTO, "descricao",
                "fallen_sustained", "immediate_risk", 11, 4, null, "outside",
                List.of(), List.of(), null,
                OffsetDateTime.of(2026, 8, 10, 14, 31, 2, 0, ZoneOffset.UTC),
                null, null, null, null, AlertaStatus.PENDENTE);
    }

    @Test
    void registraEDesregistraConexoes() {
        // Fora de um request assíncrono real, complete() não dispara os callbacks:
        // quem os invoca é a máquina de async do container. Este double captura o
        // callback de conclusão e o executa, que é exatamente o que o Tomcat faz
        // quando o browser fecha a aba.
        List<Runnable> aoConcluir = new java.util.ArrayList<>();
        SseEmitter emitter = new SseEmitter(0L) {
            @Override
            public void onCompletion(Runnable callback) {
                aoConcluir.add(callback);
                super.onCompletion(callback);
            }
        };

        service.register(emitter);
        assertThat(service.activeConnections()).isEqualTo(1);

        aoConcluir.forEach(Runnable::run);
        assertThat(service.activeConnections()).isZero();
    }

    @Test
    void subscribeRegistraUmaNovaConexao() {
        service.subscribe();

        assertThat(service.activeConnections()).isEqualTo(1);
    }

    @Test
    void entregaOIncidenteAosInscritos() {
        List<Object> recebidos = new java.util.ArrayList<>();
        SseEmitter gravador = new SseEmitter(0L) {
            @Override
            public void send(SseEventBuilder builder) {
                recebidos.add(builder.build());
            }
        };
        service.register(gravador);

        service.broadcast("incident", incidente());

        assertThat(recebidos).hasSize(1);
        assertThat(service.activeConnections()).isEqualTo(1);
    }

    @Test
    void removeConexaoQueFalhaAoReceber() {
        SseEmitter quebrado = new SseEmitter(0L) {
            @Override
            public void send(SseEventBuilder builder) throws IOException {
                throw new IOException("cliente desconectou");
            }
        };
        service.register(quebrado);
        assertThat(service.activeConnections()).isEqualTo(1);

        service.broadcast("incident", incidente());

        assertThat(service.activeConnections()).isZero();
    }

    @Test
    void heartbeatRemoveConexaoMorta() {
        SseEmitter quebrado = new SseEmitter(0L) {
            @Override
            public void send(SseEventBuilder builder) throws IOException {
                throw new IOException("cliente desconectou");
            }
        };
        service.register(quebrado);

        service.heartbeat();

        assertThat(service.activeConnections()).isZero();
    }
}
