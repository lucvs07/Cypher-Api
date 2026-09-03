package com.dev4.cypher.sse;

import com.dev4.cypher.dto.IncidentResponse;
import com.dev4.cypher.model.AlertaNivel;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.model.AlertaTipo;
import com.dev4.cypher.model.Camera;
import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.security.GestorAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class IncidentSseServiceTest {

    @Autowired
    private CameraRepository cameras;

    private IncidentSseService service;

    @BeforeEach
    void setUp() {
        service = new IncidentSseService(cameras);
        SecurityContextHolder.getContext().setAuthentication(
                new GestorAuthentication("gestor@demo", "demo"));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private IncidentResponse incidenteDe(String empresaId, String cameraId) {
        return new IncidentResponse(1L, "k", cameraId, "run-a", 7,
                AlertaTipo.FALL_RISK, 4, AlertaNivel.ALTO, "descricao",
                "fallen_sustained", "immediate_risk", 11, 4, null, "outside",
                List.of(), List.of(), null,
                OffsetDateTime.of(2026, 8, 10, 14, 31, 2, 0, ZoneOffset.UTC),
                null, null, null, null, AlertaStatus.PENDENTE, empresaId);
    }

    @Test
    void registraEDesregistraConexoes() {
        List<Runnable> aoConcluir = new ArrayList<>();
        SseEmitter emitter = new SseEmitter(0L) {
            @Override
            public void onCompletion(Runnable callback) {
                aoConcluir.add(callback);
                super.onCompletion(callback);
            }
        };

        service.register(emitter, "demo", null);
        assertThat(service.activeConnections()).isEqualTo(1);

        aoConcluir.forEach(Runnable::run);
        assertThat(service.activeConnections()).isZero();
    }

    @Test
    void subscribeComEmpresaRegistraUmaConexaoIrrestrita() {
        service.subscribe("demo");

        assertThat(service.activeConnections()).isEqualTo(1);
    }

    @Test
    void entregaOIncidenteAUmInscritoIrrestrito() {
        List<Object> recebidos = new ArrayList<>();
        SseEmitter gravador = new SseEmitter(0L) {
            @Override
            public void send(SseEventBuilder builder) {
                recebidos.add(builder.build());
            }
        };
        service.register(gravador, "demo", null);

        service.broadcast("incident", incidenteDe("demo", "CAM-01"));

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
        service.register(quebrado, "demo", null);
        assertThat(service.activeConnections()).isEqualTo(1);

        service.broadcast("incident", incidenteDe("demo", "CAM-01"));

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
        service.register(quebrado, "demo", null);

        service.heartbeat();

        assertThat(service.activeConnections()).isZero();
    }

    @Test
    void broadcastSoAlcancaInscritosDaMesmaEstacao() {
        cameras.saveAndFlush(new Camera("demo:CAM-01", "C1", "demo:EST-01",
                "http://x/1", "f", true, "key-CAM-01", "demo"));
        cameras.saveAndFlush(new Camera("demo:CAM-02", "C2", "demo:EST-02",
                "http://x/2", "f", true, "key-CAM-02", "demo"));
        List<Object> recebidos = new ArrayList<>();
        SseEmitter operadorEst01 = new SseEmitter(0L) {
            @Override
            public void send(SseEventBuilder builder) {
                recebidos.add(builder.build());
            }
        };
        service.register(operadorEst01, "demo", "EST-01");

        service.broadcast("incident", incidenteDe("demo", "CAM-02"));
        assertThat(recebidos).isEmpty();

        service.broadcast("incident", incidenteDe("demo", "CAM-01"));
        assertThat(recebidos).hasSize(1);
    }

    @Test
    void inscritoIrrestritoRecebeIncidenteDeQualquerEstacao() {
        cameras.saveAndFlush(new Camera("demo:CAM-01", "C1", "demo:EST-01",
                "http://x/1", "f", true, "key-CAM-01", "demo"));
        List<Object> recebidos = new ArrayList<>();
        SseEmitter gestor = new SseEmitter(0L) {
            @Override
            public void send(SseEventBuilder builder) {
                recebidos.add(builder.build());
            }
        };
        service.register(gestor, "demo", null);

        service.broadcast("incident", incidenteDe("demo", "CAM-01"));

        assertThat(recebidos).hasSize(1);
    }

    @Test
    void incidenteDeCameraDesconhecidaNaoAlcancaInscritoRestrito() {
        List<Object> recebidos = new ArrayList<>();
        SseEmitter operadorEst01 = new SseEmitter(0L) {
            @Override
            public void send(SseEventBuilder builder) {
                recebidos.add(builder.build());
            }
        };
        service.register(operadorEst01, "demo", "EST-01");

        service.broadcast("incident", incidenteDe("demo", "CAM-DESCONHECIDA"));

        assertThat(recebidos).isEmpty();
    }

    @Test
    void assinanteRestritoAEstacaoNaoRecebeIncidenteDeOutraEmpresaComEstacaoDeCodigoColidente() {
        // "acme" e "globex" têm, cada um, uma câmera de código puro colidente
        // ("CAM-99") numa estação também de código puro colidente ("EST-01") —
        // a comparação por código puro (Tarefa 6) não pode confundir os dois
        // tenants: só `mesmaEmpresa` deve decidir aqui, não `enxergaEstacao`.
        cameras.saveAndFlush(new Camera("globex:CAM-99", "C99", "globex:EST-01",
                "http://x/99", "f", true, "key-CAM-99-globex", "globex"));
        List<Object> recebidos = new ArrayList<>();
        SseEmitter operadorAcmeEst01 = new SseEmitter(0L) {
            @Override
            public void send(SseEventBuilder builder) {
                recebidos.add(builder.build());
            }
        };
        service.register(operadorAcmeEst01, "acme", "EST-01");

        service.broadcast("incident", incidenteDe("globex", "CAM-99"));

        assertThat(recebidos).isEmpty();
    }

    @Test
    void assinanteDeUmaEmpresaNaoRecebeIncidenteDeOutra() {
        List<Object> recebidos = new ArrayList<>();
        SseEmitter emitterAcme = new SseEmitter(0L) {
            @Override
            public void send(SseEventBuilder builder) {
                recebidos.add(builder.build());
            }
        };
        service.register(emitterAcme, "acme", null);

        IncidentResponse incidenteGlobex = incidenteDe("globex", "CAM-01");
        service.broadcast("incident", incidenteGlobex);

        assertThat(recebidos).isEmpty();
    }
}
