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
        estacoes.save(new Estacao("EST-01", "Esteira de montagem", "montagem", true, "tok-EST-01"));
        cameras.save(new Camera("CAM-01", "Frontal", "EST-01",
                "http://127.0.0.1:8090/stream", "rtsp://cam1/stream", true));
        cameras.save(new Camera("CAM-02", "Lateral", "EST-01",
                "http://127.0.0.1:8091/stream", "rtsp://cam2/stream", true));
        cameras.saveAndFlush(new Camera("CAM-03", "Expedicao", "EST-02",
                "http://127.0.0.1:8092/stream", "0", true));

        List<Camera> daEstacao = cameras.findByEstacaoId("EST-01");

        assertThat(daEstacao).extracting(Camera::getId)
                .containsExactlyInAnyOrder("CAM-01", "CAM-02");
    }

    @Test
    void estacaoGuardaSetorEEstadoAtivo() {
        estacoes.saveAndFlush(new Estacao("EST-09", "Prensa 3", "estamparia", false, "tok-EST-09"));

        Estacao lida = estacoes.findById("EST-09").orElseThrow();
        assertThat(lida.getSetor()).isEqualTo("estamparia");
        assertThat(lida.isAtiva()).isFalse();
    }

    @Test
    void listaApenasCamerasAtivas() {
        cameras.save(new Camera("CAM-01", "Frontal", "EST-01",
                "http://127.0.0.1:8090/stream", "rtsp://cam1/stream", true));
        cameras.saveAndFlush(new Camera("CAM-02", "Desativada", "EST-01",
                "http://127.0.0.1:8091/stream", "rtsp://cam2/stream", false));

        assertThat(cameras.findByAtivaTrue()).extracting(Camera::getId)
                .containsExactly("CAM-01");
    }

    @Test
    void listaCamerasAtivasDeUmaEstacao() {
        cameras.save(new Camera("CAM-01", "Frontal", "EST-01",
                "http://127.0.0.1:8090/stream", "rtsp://a", true));
        cameras.save(new Camera("CAM-02", "Inativa", "EST-01",
                "http://127.0.0.1:8091/stream", "rtsp://b", false));
        cameras.saveAndFlush(new Camera("CAM-03", "Outra estacao", "EST-02",
                "http://127.0.0.1:8092/stream", "rtsp://c", true));

        assertThat(cameras.findByEstacaoIdAndAtivaTrue("EST-01"))
                .extracting(Camera::getId).containsExactly("CAM-01");
    }

    @Test
    void encontraCameraPelaUrlDeStream() {
        cameras.saveAndFlush(new Camera("CAM-01", "Frontal", "EST-01",
                "http://127.0.0.1:8090/stream", "rtsp://a", true));

        assertThat(cameras.findByStreamUrl("http://127.0.0.1:8090/stream"))
                .isPresent();
        assertThat(cameras.findByStreamUrl("http://127.0.0.1:9999/stream"))
                .isEmpty();
    }

    @Test
    void listaApenasEstacoesAtivas() {
        estacoes.save(new Estacao("EST-01", "Ativa", "montagem", true, "tok-EST-01"));
        estacoes.saveAndFlush(new Estacao("EST-02", "Inativa", "montagem", false, "tok-EST-02"));

        assertThat(estacoes.findByAtivaTrue()).extracting(Estacao::getId)
                .containsExactly("EST-01");
    }

    @Test
    void encontraEstacaoPeloToken() {
        estacoes.saveAndFlush(new Estacao("EST-30", "Expedicao", "logistica", true, "tok-EST-30"));

        assertThat(estacoes.findByToken("tok-EST-30")).isPresent();
        assertThat(estacoes.findByToken("tok-EST-30").orElseThrow().getId()).isEqualTo("EST-30");
        assertThat(estacoes.findByToken("token-que-nao-existe")).isEmpty();
    }
}
