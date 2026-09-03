package com.dev4.cypher;

import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.model.AlertaNivel;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.model.AlertaTipo;
import com.dev4.cypher.model.Camera;
import com.dev4.cypher.model.Estacao;
import com.dev4.cypher.repository.AlertaRepository;
import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.repository.EstacaoRepository;
import com.dev4.cypher.security.AuthTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static com.dev4.cypher.security.AuthTestSupport.loginGestorBootstrap;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "cypher.catalog.stations[0].id=EST-01",
        "cypher.catalog.stations[0].nome=Esteira de montagem",
        "cypher.catalog.stations[0].setor=montagem",
        "cypher.catalog.stations[1].id=EST-02",
        "cypher.catalog.stations[1].nome=Prensa 3",
        "cypher.catalog.stations[1].setor=estamparia",
        "cypher.catalog.cameras[0].id=CAM-01",
        "cypher.catalog.cameras[0].nome=Frontal",
        "cypher.catalog.cameras[0].estacaoId=EST-01",
        "cypher.catalog.cameras[1].id=CAM-02",
        "cypher.catalog.cameras[1].nome=Lateral",
        "cypher.catalog.cameras[1].estacaoId=EST-02",
})
class SummaryApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AlertaRepository repository;

    @Autowired
    private EstacaoRepository estacoes;

    @Autowired
    private CameraRepository cameras;

    private MockHttpSession gestor;

    private Alerta incidente(String key, String camera, int severidade, OffsetDateTime quando) {
        Alerta a = new Alerta();
        a.setIncidentKey(key);
        a.setRunId("run-s");
        a.setTrackId(1);
        a.setCameraId(camera);
        a.setTipo(AlertaTipo.FALL_RISK);
        a.setSeveridade(severidade);
        a.setNivel(AlertaNivel.fromSeverity(severidade));
        a.setDescricao("teste");
        a.setReason("regra");
        a.setPosturaCategoria("immediate_risk");
        a.setRebaScore(9);
        a.setActionLevel(3);
        a.setZoneStatus("outside");
        a.setEpiPresent(List.of());
        a.setEpiMissing(List.of());
        a.setDataHora(quando);
        a.setStatus(AlertaStatus.PENDENTE);
        a.setEmpresaId(AuthTestSupport.EMPRESA_ID);
        return a;
    }

    /**
     * Estações/câmeras próprias do tenant de teste ({@code acme-test}), não
     * as que o {@code CatalogSeeder} semeia sob {@code demo} a partir das
     * propriedades acima — desde que {@code CatalogService.stations()}
     * passou a ser escopado por tenant (Tarefa 4), usar o catálogo do
     * {@code demo} sob uma sessão {@code acme-test} não aparece mais no
     * heatmap.
     */
    private void semearCatalogoDoTenant() {
        String est01 = "acme-test:EST-01";
        String est02 = "acme-test:EST-02";
        estacoes.save(new Estacao(est01, "Esteira de montagem", "montagem", true,
                "tok-acme-test-EST-01", AuthTestSupport.EMPRESA_ID));
        estacoes.saveAndFlush(new Estacao(est02, "Prensa 3", "estamparia", true,
                "tok-acme-test-EST-02", AuthTestSupport.EMPRESA_ID));
        cameras.save(new Camera("acme-test:CAM-01", "Frontal", est01,
                "http://127.0.0.1:9090/stream", "rtsp://a", true,
                "key-acme-test-CAM-01", AuthTestSupport.EMPRESA_ID));
        cameras.saveAndFlush(new Camera("acme-test:CAM-02", "Lateral", est02,
                "http://127.0.0.1:9091/stream", "rtsp://b", true,
                "key-acme-test-CAM-02", AuthTestSupport.EMPRESA_ID));
    }

    @BeforeEach
    void semear() throws Exception {
        gestor = loginGestorBootstrap(mvc);
        semearCatalogoDoTenant();
        repository.deleteAll();
        OffsetDateTime agora = OffsetDateTime.now();
        repository.save(incidente("s1", "CAM-01", 4, agora.minusHours(1)));
        repository.save(incidente("s2", "CAM-01", 2, agora.minusHours(2)));
        repository.save(incidente("s3", "CAM-02", 3, agora.minusHours(3)));
        repository.saveAndFlush(incidente("s4", "CAM-01", 1, agora.minusDays(1)));
    }

    @Test
    void contaIncidentesDeHojeEDeOntem() throws Exception {
        mvc.perform(get("/api/v1/summary").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentesHoje").value(3))
                .andExpect(jsonPath("$.incidentesOntem").value(1));
    }

    @Test
    void contaCriticosEPendentes() throws Exception {
        mvc.perform(get("/api/v1/summary").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criticosHoje").value(2))
                .andExpect(jsonPath("$.pendentes").value(4));
    }

    @Test
    void heatmapAgregaPorEstacaoIncluindoAsSemIncidente() throws Exception {
        mvc.perform(get("/api/v1/summary").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heatmap.length()").value(2))
                .andExpect(jsonPath("$.heatmap[?(@.estacaoId=='EST-01')].incidentes").value(2))
                .andExpect(jsonPath("$.heatmap[?(@.estacaoId=='EST-02')].incidentes").value(1));
    }

    @Test
    void serieDeSeteDiasTemUmPontoPorDia() throws Exception {
        mvc.perform(get("/api/v1/summary").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimosSeteDias.length()").value(7));
    }
}
