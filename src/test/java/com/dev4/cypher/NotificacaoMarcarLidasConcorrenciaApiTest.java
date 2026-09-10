package com.dev4.cypher;

import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.model.AlertaNivel;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.model.AlertaTipo;
import com.dev4.cypher.repository.AlertaLeituraRepository;
import com.dev4.cypher.repository.AlertaRepository;
import com.dev4.cypher.service.NotificacaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, com transações reais commitadas (SEM {@code @Transactional} na
 * classe de teste — ao contrário de {@link NotificacaoControllerApiTest}),
 * que {@code marcarLidas} não deixa vazar {@code UnexpectedRollbackException}
 * quando duas chamadas concorrentes disputam o mesmo alerta.
 *
 * <p>{@link com.dev4.cypher.service.NotificacaoServiceTest} já cobre o mesmo
 * caminho de código com mocks (determinístico, mas não prova nada sobre o
 * comportamento transacional real — um {@code @Transactional} do lado de
 * fora do serviço não existe num teste com repositório mockado). Este teste
 * aqui usa o {@code NotificacaoService} de verdade, contra o H2 real, com
 * várias threads batendo no mesmo id ao mesmo tempo via {@link CyclicBarrier}
 * para maximizar a chance de duas delas caírem na janela de corrida entre a
 * consulta de "já lidos" e o {@code saveAll} — achado da revisão final da
 * Tarefa 3 (o {@code @Transactional} do método antigo fazia o commit final
 * lançar {@code UnexpectedRollbackException} nesse cenário).
 */
@SpringBootTest
class NotificacaoMarcarLidasConcorrenciaApiTest {

    private static final String EMPRESA_ID = "acme-test";
    private static final String USUARIO = "concorrencia-not@acme-test";

    @Autowired
    private NotificacaoService service;

    @Autowired
    private AlertaRepository alertas;

    @Autowired
    private AlertaLeituraRepository leituras;

    @Test
    void chamadasConcorrentesParaOMesmoAlertaNaoLancamExcecaoENaoDuplicamLeitura() throws Exception {
        Long alertaId = alertas.saveAndFlush(alertaFixture()).getId();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier largada = new CyclicBarrier(threads);
        List<Future<Exception>> resultados = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            resultados.add(pool.submit(() -> {
                try {
                    largada.await();
                    service.marcarLidas(EMPRESA_ID, USUARIO, List.of(alertaId));
                    return null;
                } catch (Exception e) {
                    return e;
                }
            }));
        }

        for (Future<Exception> resultado : resultados) {
            Exception excecao = resultado.get(10, TimeUnit.SECONDS);
            assertThat(excecao).isNull();
        }
        pool.shutdown();

        assertThat(leituras.existsByEmpresaIdAndUsuarioAndAlertaId(EMPRESA_ID, USUARIO, alertaId)).isTrue();
        assertThat(leituras.findByEmpresaIdAndUsuarioAndAlertaIdIn(EMPRESA_ID, USUARIO, List.of(alertaId)))
                .hasSize(1);
    }

    private Alerta alertaFixture() {
        Alerta a = new Alerta();
        a.setIncidentKey("concorrencia-not:" + System.nanoTime());
        a.setCameraId(EMPRESA_ID + ":CAM-CONC");
        a.setRunId("run-conc");
        a.setTrackId(1);
        a.setTipo(AlertaTipo.FALL_RISK);
        a.setSeveridade(4);
        a.setNivel(AlertaNivel.fromSeverity(4));
        a.setReason("r");
        a.setPosturaCategoria("immediate_risk");
        a.setRebaScore(11);
        a.setActionLevel(4);
        a.setZoneStatus("outside");
        a.setDescricao("Fixture de teste de concorrência");
        a.setDataHora(OffsetDateTime.parse("2026-09-01T10:00:00Z"));
        a.setStatus(AlertaStatus.PENDENTE);
        a.setEmpresaId(EMPRESA_ID);
        return a;
    }
}
