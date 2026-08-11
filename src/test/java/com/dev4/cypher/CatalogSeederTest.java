package com.dev4.cypher;

import com.dev4.cypher.model.Camera;
import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.repository.EstacaoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
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

    @Test
    void populaCatalogoAPartirDaConfiguracao() {
        assertThat(estacoes.findById("EST-01")).isPresent();

        Camera cam = cameras.findById("CAM-01").orElseThrow();
        assertThat(cam.getEstacaoId()).isEqualTo("EST-01");
        assertThat(cam.getStreamUrl()).isEqualTo("http://127.0.0.1:8090/stream");
    }
}
