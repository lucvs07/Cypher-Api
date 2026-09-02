package com.dev4.cypher;

import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.model.AlertaNivel;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.model.AlertaTipo;
import com.dev4.cypher.repository.AlertaRepository;
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
        a.setEmpresaId("demo");
        return a;
    }

    @BeforeEach
    void semear() throws Exception {
        gestor = loginGestorBootstrap(mvc);
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
