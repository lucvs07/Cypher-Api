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
        estacoes.save(new Estacao("EST-01", "Esteira de montagem", "montagem", true));
        cameras.save(new Camera("CAM-01", "Frontal", "EST-01",
                "http://127.0.0.1:8090/stream", "rtsp://cam1/stream"));
        cameras.save(new Camera("CAM-02", "Lateral", "EST-01",
                "http://127.0.0.1:8091/stream", "rtsp://cam2/stream"));
        cameras.saveAndFlush(new Camera("CAM-03", "Expedicao", "EST-02",
                "http://127.0.0.1:8092/stream", "0"));

        List<Camera> daEstacao = cameras.findByEstacaoId("EST-01");

        assertThat(daEstacao).extracting(Camera::getId)
                .containsExactlyInAnyOrder("CAM-01", "CAM-02");
    }

    @Test
    void estacaoGuardaSetorEEstadoAtivo() {
        estacoes.saveAndFlush(new Estacao("EST-09", "Prensa 3", "estamparia", false));

        Estacao lida = estacoes.findById("EST-09").orElseThrow();
        assertThat(lida.getSetor()).isEqualTo("estamparia");
        assertThat(lida.isAtiva()).isFalse();
    }
}
