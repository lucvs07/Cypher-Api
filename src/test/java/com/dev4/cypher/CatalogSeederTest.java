package com.dev4.cypher;

import com.dev4.cypher.config.CatalogSeeder;
import com.dev4.cypher.model.Camera;
import com.dev4.cypher.model.Estacao;
import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.repository.EstacaoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "cypher.catalog.stations[0].id=EST-01",
        "cypher.catalog.stations[0].nome=Esteira de montagem",
        "cypher.catalog.stations[0].setor=montagem",
        "cypher.catalog.cameras[0].id=CAM-01",
        "cypher.catalog.cameras[0].nome=Frontal",
        "cypher.catalog.cameras[0].estacaoId=EST-01",
        "cypher.catalog.cameras[0].streamUrl=http://127.0.0.1:8090/stream",
        "cypher.catalog.cameras[0].fonte=data/validation/cenario2.mp4",
})
class CatalogSeederTest {

    @Autowired
    private EstacaoRepository estacoes;

    @Autowired
    private CameraRepository cameras;

    @Autowired
    private CatalogSeeder seeder;

    @Test
    void populaCatalogoAPartirDaConfiguracao() {
        assertThat(estacoes.findById("EST-01")).isPresent();

        Camera cam = cameras.findById("CAM-01").orElseThrow();
        assertThat(cam.getEstacaoId()).isEqualTo("EST-01");
        assertThat(cam.getStreamUrl()).isEqualTo("http://127.0.0.1:8090/stream");
        assertThat(cam.isAtiva()).isTrue();
    }

    @Test
    void naoSobrescreveEdicaoFeitaPelaInterface() {
        // Simula o que a interface faria: renomear a estação semeada.
        Estacao editada = estacoes.findById("EST-01").orElseThrow();
        editada.setNome("Esteira 1 - renomeada");
        estacoes.saveAndFlush(editada);

        // Reiniciar a aplicação executaria o seeder de novo.
        seeder.run(null);

        assertThat(estacoes.findById("EST-01").orElseThrow().getNome())
                .isEqualTo("Esteira 1 - renomeada");
    }

    @Test
    void naoReativaRegistroQueAInterfaceDesativou() {
        Camera desativada = cameras.findById("CAM-01").orElseThrow();
        desativada.setAtiva(false);
        cameras.saveAndFlush(desativada);

        seeder.run(null);

        assertThat(cameras.findById("CAM-01").orElseThrow().isAtiva()).isFalse();
    }

    @Test
    void semearDeNovoNaoDuplica() {
        long estacoesAntes = estacoes.count();
        long camerasAntes = cameras.count();

        seeder.run(null);

        assertThat(estacoes.count()).isEqualTo(estacoesAntes);
        assertThat(cameras.count()).isEqualTo(camerasAntes);
    }
}
