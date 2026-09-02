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
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static com.dev4.cypher.security.AuthTestSupport.loginGestorBootstrap;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "cypher.catalog.stations[0].id=EST-01",
        "cypher.catalog.stations[0].nome=Esteira de montagem",
        "cypher.catalog.stations[0].setor=montagem",
        "cypher.catalog.cameras[0].id=CAM-01",
        "cypher.catalog.cameras[0].nome=Frontal",
        "cypher.catalog.cameras[0].estacaoId=EST-01",
        "cypher.catalog.cameras[0].streamUrl=http://127.0.0.1:8090/stream",
        "cypher.catalog.cameras[0].fonte=rtsp://user:senha@cam/stream",
})
class CatalogApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AlertaRepository alertas;

    private MockHttpSession gestor;

    @BeforeEach
    void autenticar() throws Exception {
        gestor = loginGestorBootstrap(mvc);
    }

    @Test
    void listaCamerasComUrlDeStream() throws Exception {
        mvc.perform(get("/api/v1/cameras").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("CAM-01"))
                .andExpect(jsonPath("$[0].estacaoId").value("EST-01"))
                .andExpect(jsonPath("$[0].streamUrl").value("http://127.0.0.1:8090/stream"));
    }

    @Test
    void naoExpoeAFonteQuePodeConterCredencial() throws Exception {
        mvc.perform(get("/api/v1/cameras").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fonte").doesNotExist());
    }

    @Test
    void listaEstacoesComSuasCameras() throws Exception {
        mvc.perform(get("/api/v1/stations").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("EST-01"))
                .andExpect(jsonPath("$[0].setor").value("montagem"))
                .andExpect(jsonPath("$[0].cameras[0].id").value("CAM-01"));
    }

    @Test
    @Transactional
    void desativarCameraATiraDaListaSemApagarSeusIncidentes() throws Exception {
        Alerta incidente = new Alerta();
        incidente.setIncidentKey("CAM-01:run-e2:track-1:20260820T120000000Z");
        incidente.setRunId("run-e2");
        incidente.setTrackId(1);
        incidente.setCameraId("CAM-01");
        incidente.setTipo(AlertaTipo.FALL_RISK);
        incidente.setSeveridade(4);
        incidente.setNivel(AlertaNivel.fromSeverity(4));
        incidente.setDescricao("Queda");
        incidente.setReason("fallen_sustained");
        incidente.setPosturaCategoria("immediate_risk");
        incidente.setRebaScore(11);
        incidente.setActionLevel(4);
        incidente.setZoneStatus("outside");
        incidente.setEpiPresent(List.of());
        incidente.setEpiMissing(List.of());
        incidente.setDataHora(OffsetDateTime.now());
        incidente.setStatus(AlertaStatus.PENDENTE);
        incidente.setEmpresaId("demo");
        alertas.saveAndFlush(incidente);

        mvc.perform(delete("/api/v1/cameras/CAM-01").session(gestor)).andExpect(status().isNoContent());

        // Saiu da navegação...
        mvc.perform(get("/api/v1/cameras").session(gestor))
                .andExpect(jsonPath("$[?(@.id=='CAM-01')]").isEmpty());

        // ...mas o incidente continua consultável.
        mvc.perform(get("/api/v1/incidents").param("camera", "CAM-01").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
