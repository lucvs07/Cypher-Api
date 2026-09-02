package com.dev4.cypher.repository;

import com.dev4.cypher.model.Camera;
import com.dev4.cypher.model.Estacao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CatalogRepositoryTest {

    @Autowired
    private EstacaoRepository estacoes;

    @Autowired
    private CameraRepository cameras;

    @Test
    void umaEstacaoEhCobertaPorVariasCameras() {
        estacoes.save(new Estacao("demo:EST-01", "Esteira de montagem", "montagem", true, "tok-EST-01", "demo"));
        cameras.save(new Camera("demo:CAM-01", "Frontal", "demo:EST-01",
                "http://127.0.0.1:8090/stream", "rtsp://cam1/stream", true, "key-CAM-01", "demo"));
        cameras.save(new Camera("demo:CAM-02", "Lateral", "demo:EST-01",
                "http://127.0.0.1:8091/stream", "rtsp://cam2/stream", true, "key-CAM-02", "demo"));
        cameras.saveAndFlush(new Camera("demo:CAM-03", "Expedicao", "demo:EST-02",
                "http://127.0.0.1:8092/stream", "0", true, "key-CAM-03", "demo"));

        List<Camera> daEstacao = cameras.findByEstacaoId("demo:EST-01");

        assertThat(daEstacao).extracting(Camera::getId)
                .containsExactlyInAnyOrder("demo:CAM-01", "demo:CAM-02");
    }

    @Test
    void chaveArmazenadaDeEstacaoEhPrefixadaPeloTenant() {
        estacoes.saveAndFlush(new Estacao("demo:EST-77", "Prefixada", "montagem", true, "tok-EST-77", "demo"));

        Estacao lida = estacoes.findById("demo:EST-77").orElseThrow();
        assertThat(lida.getEmpresaId()).isEqualTo("demo");
    }

    @Test
    void estacaoGuardaSetorEEstadoAtivo() {
        estacoes.saveAndFlush(new Estacao("demo:EST-09", "Prensa 3", "estamparia", false, "tok-EST-09", "demo"));

        Estacao lida = estacoes.findById("demo:EST-09").orElseThrow();
        assertThat(lida.getSetor()).isEqualTo("estamparia");
        assertThat(lida.isAtiva()).isFalse();
    }

    @Test
    void listaApenasCamerasAtivas() {
        cameras.save(new Camera("demo:CAM-01", "Frontal", "demo:EST-01",
                "http://127.0.0.1:8090/stream", "rtsp://cam1/stream", true, "key-CAM-01", "demo"));
        cameras.saveAndFlush(new Camera("demo:CAM-02", "Desativada", "demo:EST-01",
                "http://127.0.0.1:8091/stream", "rtsp://cam2/stream", false, "key-CAM-02", "demo"));

        assertThat(cameras.findByAtivaTrue()).extracting(Camera::getId)
                .containsExactly("demo:CAM-01");
    }

    @Test
    void listaCamerasAtivasDeUmaEstacao() {
        cameras.save(new Camera("demo:CAM-01", "Frontal", "demo:EST-01",
                "http://127.0.0.1:8090/stream", "rtsp://a", true, "key-CAM-01", "demo"));
        cameras.save(new Camera("demo:CAM-02", "Inativa", "demo:EST-01",
                "http://127.0.0.1:8091/stream", "rtsp://b", false, "key-CAM-02", "demo"));
        cameras.saveAndFlush(new Camera("demo:CAM-03", "Outra estacao", "demo:EST-02",
                "http://127.0.0.1:8092/stream", "rtsp://c", true, "key-CAM-03", "demo"));

        assertThat(cameras.findByEstacaoIdAndAtivaTrue("demo:EST-01"))
                .extracting(Camera::getId).containsExactly("demo:CAM-01");
    }

    @Test
    void encontraCameraPelaUrlDeStream() {
        cameras.saveAndFlush(new Camera("demo:CAM-01", "Frontal", "demo:EST-01",
                "http://127.0.0.1:8090/stream", "rtsp://a", true, "key-CAM-01", "demo"));

        assertThat(cameras.findByStreamUrl("http://127.0.0.1:8090/stream"))
                .isPresent();
        assertThat(cameras.findByStreamUrl("http://127.0.0.1:9999/stream"))
                .isEmpty();
    }

    @Test
    void listaApenasEstacoesAtivas() {
        estacoes.save(new Estacao("demo:EST-01", "Ativa", "montagem", true, "tok-EST-01", "demo"));
        estacoes.saveAndFlush(new Estacao("demo:EST-02", "Inativa", "montagem", false, "tok-EST-02", "demo"));

        assertThat(estacoes.findByAtivaTrue()).extracting(Estacao::getId)
                .containsExactly("demo:EST-01");
    }

    @Test
    void encontraEstacaoPeloToken() {
        estacoes.saveAndFlush(new Estacao("demo:EST-30", "Expedicao", "logistica", true, "tok-EST-30", "demo"));

        assertThat(estacoes.findByToken("tok-EST-30")).isPresent();
        assertThat(estacoes.findByToken("tok-EST-30").orElseThrow().getId()).isEqualTo("demo:EST-30");
        assertThat(estacoes.findByToken("token-que-nao-existe")).isEmpty();
    }

    @Test
    void encontraCameraPelaApiKey() {
        cameras.saveAndFlush(new Camera("demo:CAM-30", "Frontal", "demo:EST-30",
                "http://127.0.0.1:8230/stream", "rtsp://a", true, "key-CAM-30", "demo"));

        assertThat(cameras.findByApiKey("key-CAM-30")).isPresent();
        assertThat(cameras.findByApiKey("key-CAM-30").orElseThrow().getId()).isEqualTo("demo:CAM-30");
        assertThat(cameras.findByApiKey("chave-que-nao-existe")).isEmpty();
    }
}
