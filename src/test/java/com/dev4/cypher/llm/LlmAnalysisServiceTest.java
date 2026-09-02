package com.dev4.cypher.llm;

import com.dev4.cypher.catalog.CatalogValidationException;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.model.AnalysisStatus;
import com.dev4.cypher.dto.IncidentRequest;
import com.dev4.cypher.repository.AlertaRepository;
import com.dev4.cypher.repository.IncidentLlmAnalysisRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LlmAnalysisServiceTest {

    private static HttpServer servidor;
    private static volatile int statusDeResposta = 200;
    private static volatile String corpoDeResposta =
            "{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"situacao\\\":\\\"trabalhador ok\\\","
                    + "\\\"epis_identificados\\\":[],\\\"epis_ausentes\\\":[],"
                    + "\\\"risco_identificado\\\":false,\\\"tipo_risco\\\":null,"
                    + "\\\"nivel_risco\\\":\\\"baixo\\\",\\\"justificativa\\\":\\\"nada de anormal\\\","
                    + "\\\"acao_recomendada\\\":\\\"nenhuma\\\"}\"}]}";

    /**
     * Fila de status agendados para as proximas chamadas — usada so pelo
     * teste de retry, para simular "falha transitoria duas vezes, depois
     * sucesso". Vazia (comportamento legado): toda chamada usa
     * {@code statusDeResposta} direto, sem depender de contagem.
     */
    private static final java.util.Queue<Integer> statusAgendados =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** Conta quantas vezes o "provedor" (servidor fake) foi de fato chamado. */
    private static final java.util.concurrent.atomic.AtomicInteger chamadasRecebidas =
            new java.util.concurrent.atomic.AtomicInteger(0);

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1/messages", troca -> {
            chamadasRecebidas.incrementAndGet();
            Integer agendado = statusAgendados.poll();
            int status = agendado != null ? agendado : statusDeResposta;
            byte[] resposta = status == 200 ? corpoDeResposta.getBytes() : "{}".getBytes();
            troca.sendResponseHeaders(status, resposta.length);
            troca.getResponseBody().write(resposta);
            troca.close();
        });
        servidor.start();

        registry.add("cypher.llm.provider", () -> "claude");
        registry.add("cypher.llm.claude.base-url",
                () -> "http://127.0.0.1:" + servidor.getAddress().getPort());
        registry.add("cypher.llm.claude.api-key", () -> "chave-de-teste");
        registry.add("cypher.llm.claude.model", () -> "claude-teste");
    }

    @AfterAll
    static void derrubarServidor() {
        servidor.stop(0);
    }

    @Autowired
    private LlmAnalysisService service;

    @Autowired
    private AlertaRepository alertas;

    @Autowired
    private IncidentLlmAnalysisRepository analyses;

    @BeforeEach
    void limpar() {
        alertas.deleteAll();
        analyses.deleteAll();
        statusAgendados.clear();
        statusDeResposta = 200;
        corpoDeResposta = "{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"situacao\\\":\\\"trabalhador ok\\\","
                + "\\\"epis_identificados\\\":[],\\\"epis_ausentes\\\":[],"
                + "\\\"risco_identificado\\\":false,\\\"tipo_risco\\\":null,"
                + "\\\"nivel_risco\\\":\\\"baixo\\\",\\\"justificativa\\\":\\\"nada de anormal\\\","
                + "\\\"acao_recomendada\\\":\\\"nenhuma\\\"}\"}]}";
    }

    private com.dev4.cypher.model.Alerta salvarAlertaComSnapshot(String snapshotUrl) {
        com.dev4.cypher.model.Alerta alerta = new com.dev4.cypher.model.Alerta();
        alerta.setIncidentKey("k-" + System.nanoTime());
        alerta.setRunId("run-teste");
        alerta.setTrackId(1);
        alerta.setCameraId("CAM-01");
        alerta.setTipo(com.dev4.cypher.model.AlertaTipo.EPI_VIOLATION);
        alerta.setSeveridade(2);
        alerta.setNivel(com.dev4.cypher.model.AlertaNivel.fromSeverity(2));
        alerta.setDescricao("EPI ausente detectado na camera CAM-01");
        alerta.setReason("epi_missing");
        alerta.setPosturaCategoria("adequate");
        alerta.setEpiPresent(java.util.List.of());
        alerta.setEpiMissing(java.util.List.of("luvas"));
        alerta.setRebaScore(3);
        alerta.setActionLevel(1);
        alerta.setZoneStatus("outside");
        alerta.setSnapshotUrl(snapshotUrl);
        alerta.setDataHora(OffsetDateTime.now());
        alerta.setStatus(AlertaStatus.PENDENTE);
        alerta.setEmpresaId("demo");
        return alertas.save(alerta);
    }

    @Test
    void caminhoFelizDevolveAnaliseEPersisteAuditoria() {
        var alerta = salvarAlertaComSnapshot("https://exemplo.com/foto.jpg");

        var resultado = service.analisar(alerta.getId());

        assertThat(resultado.status()).isEqualTo(AnalysisStatus.OK);
        assertThat(resultado.situacao()).isEqualTo("trabalhador ok");
        assertThat(analyses.findAll()).hasSize(1);
        assertThat(analyses.findAll().get(0).getAlertaId()).isEqualTo(alerta.getId());
    }

    @Test
    void semSnapshotUrlLanca422() {
        var alerta = salvarAlertaComSnapshot(null);

        assertThatThrownBy(() -> service.analisar(alerta.getId()))
                .isInstanceOf(CatalogValidationException.class)
                .satisfies(e -> assertThat(((CatalogValidationException) e).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void alertaInexistenteLancaNoSuchElement() {
        assertThatThrownBy(() -> service.analisar(999999L))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void falhaDoProvedorVideErroApiSemLancarExcecao() {
        var alerta = salvarAlertaComSnapshot("https://exemplo.com/foto.jpg");
        statusDeResposta = 500;

        var resultado = service.analisar(alerta.getId());

        assertThat(resultado.status()).isEqualTo(AnalysisStatus.ERRO_API);
        assertThat(analyses.findAll()).hasSize(1);
        assertThat(analyses.findAll().get(0).getStatus()).isEqualTo(AnalysisStatus.ERRO_API);
    }

    @Test
    void falhaTransitoriaTentaDeNovoEAcabaComSucesso() {
        var alerta = salvarAlertaComSnapshot("https://exemplo.com/foto.jpg");
        // 503 nas duas primeiras chamadas (transitorio, retentavel); a
        // terceira cai no comportamento padrao (200 + corpoDeResposta).
        statusAgendados.offer(503);
        statusAgendados.offer(503);

        var resultado = service.analisar(alerta.getId());

        assertThat(resultado.status()).isEqualTo(AnalysisStatus.OK);
        assertThat(resultado.situacao()).isEqualTo("trabalhador ok");
        assertThat(statusAgendados).isEmpty();
    }

    @Test
    void ultimaAnaliseSemNenhumaChamadaAntesDevolveVazio() {
        var alerta = salvarAlertaComSnapshot("https://exemplo.com/foto.jpg");

        assertThat(service.ultimaAnalise(alerta.getId())).isEmpty();
    }

    @Test
    void ultimaAnaliseReconstroiAUltimaChamadaSemChamarOProvedorDeNovo() {
        var alerta = salvarAlertaComSnapshot("https://exemplo.com/foto.jpg");
        service.analisar(alerta.getId());
        int chamadasAoServidorAntes = chamadasRecebidas.get();

        var ultima = service.ultimaAnalise(alerta.getId());

        assertThat(ultima).isPresent();
        assertThat(ultima.get().status()).isEqualTo(AnalysisStatus.OK);
        assertThat(ultima.get().situacao()).isEqualTo("trabalhador ok");
        assertThat(ultima.get().provider()).isEqualTo("claude");
        // Reconstruida do que ja estava salvo — nao chamou o provedor de novo.
        assertThat(chamadasRecebidas.get()).isEqualTo(chamadasAoServidorAntes);
    }

    @Test
    void ultimaAnaliseDevolveASemprseMaisRecenteQuandoHaVariasChamadas() {
        var alerta = salvarAlertaComSnapshot("https://exemplo.com/foto.jpg");
        service.analisar(alerta.getId());
        corpoDeResposta = "{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"situacao\\\":\\\"segunda chamada\\\","
                + "\\\"epis_identificados\\\":[],\\\"epis_ausentes\\\":[],"
                + "\\\"risco_identificado\\\":false,\\\"tipo_risco\\\":null,"
                + "\\\"nivel_risco\\\":\\\"baixo\\\",\\\"justificativa\\\":\\\"nada de anormal\\\","
                + "\\\"acao_recomendada\\\":\\\"nenhuma\\\"}\"}]}";
        service.analisar(alerta.getId());

        var ultima = service.ultimaAnalise(alerta.getId());

        assertThat(ultima).isPresent();
        assertThat(ultima.get().situacao()).isEqualTo("segunda chamada");
    }

    @Test
    void ultimaAnaliseReconstroiFalhaDeApiSemRespostaJson() {
        var alerta = salvarAlertaComSnapshot("https://exemplo.com/foto.jpg");
        statusDeResposta = 500;
        service.analisar(alerta.getId());

        var ultima = service.ultimaAnalise(alerta.getId());

        assertThat(ultima).isPresent();
        assertThat(ultima.get().status()).isEqualTo(AnalysisStatus.ERRO_API);
    }

    @Test
    void respostaMalformadaVideErroParseSemLancarExcecao() {
        var alerta = salvarAlertaComSnapshot("https://exemplo.com/foto.jpg");
        corpoDeResposta = "{\"content\":[{\"type\":\"text\",\"text\":\"desculpe, nao consigo analisar\"}]}";

        var resultado = service.analisar(alerta.getId());

        assertThat(resultado.status()).isEqualTo(AnalysisStatus.ERRO_PARSE);
        assertThat(analyses.findAll()).hasSize(1);
        assertThat(analyses.findAll().get(0).getRespostaJson()).isNotBlank();
    }
}
