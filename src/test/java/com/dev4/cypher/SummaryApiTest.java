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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    /**
     * Achado carregado da Tarefa 4 (confirmado real nesta revisão): antes
     * desta correção, {@code SummaryService.build()} chamava
     * {@code repository.findAll()} sem filtro de tenant — todo GESTOR via
     * `/summary` enxergava as contagens e o heatmap de QUALQUER empresa.
     *
     * <p>O incidente de "outro-tenant" usa {@code cameraId} JÁ PREFIXADO
     * ("outro-tenant:CAM-01"), o formato real gravado por
     * {@code IncidentMapper} desde a Tarefa 5 — prova que
     * {@code SummaryService} desprefixa corretamente antes de casar com
     * {@code CameraResponse.id()} (código puro) no heatmap.
     */
    @Test
    void resumoNaoVazaContagensNemHeatmapDeOutroTenant() throws Exception {
        MockHttpSession outroGestor = new MockHttpSession();
        mvc.perform(post("/api/v1/setup").session(outroGestor)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"admin\", \"password\": \"admin123\", "
                                + "\"empresaId\": \"outro-tenant\", \"empresaNome\": \"Outro Tenant\"}"))
                .andExpect(status().isOk());

        estacoes.saveAndFlush(new Estacao("outro-tenant:EST-01", "Estacao de outro tenant", "setor-b", true,
                "tok-outro-tenant-EST-01", "outro-tenant"));
        cameras.saveAndFlush(new Camera("outro-tenant:CAM-01", "Frontal de outro tenant",
                "outro-tenant:EST-01", "http://127.0.0.1:9099/stream", "rtsp://c", true,
                "key-outro-tenant-CAM-01", "outro-tenant"));

        Alerta incidenteOutroTenant = new Alerta();
        incidenteOutroTenant.setIncidentKey("outro-tenant:outro-s1:run-s:1:20260810T000000000Z");
        incidenteOutroTenant.setRunId("run-s");
        incidenteOutroTenant.setTrackId(1);
        incidenteOutroTenant.setCameraId("outro-tenant:CAM-01");
        incidenteOutroTenant.setTipo(AlertaTipo.FALL_RISK);
        incidenteOutroTenant.setSeveridade(4);
        incidenteOutroTenant.setNivel(AlertaNivel.fromSeverity(4));
        incidenteOutroTenant.setDescricao("teste outro tenant");
        incidenteOutroTenant.setReason("regra");
        incidenteOutroTenant.setPosturaCategoria("immediate_risk");
        incidenteOutroTenant.setRebaScore(9);
        incidenteOutroTenant.setActionLevel(3);
        incidenteOutroTenant.setZoneStatus("outside");
        incidenteOutroTenant.setEpiPresent(List.of());
        incidenteOutroTenant.setEpiMissing(List.of());
        incidenteOutroTenant.setDataHora(OffsetDateTime.now().minusHours(1));
        incidenteOutroTenant.setStatus(AlertaStatus.PENDENTE);
        incidenteOutroTenant.setEmpresaId("outro-tenant");
        repository.saveAndFlush(incidenteOutroTenant);

        // Tenant original ("acme-test"): as contagens e o heatmap continuam
        // exatamente como nos testes acima — nada do tenant novo aparece.
        mvc.perform(get("/api/v1/summary").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentesHoje").value(3))
                .andExpect(jsonPath("$.pendentes").value(4))
                .andExpect(jsonPath("$.heatmap.length()").value(2))
                .andExpect(jsonPath("$.heatmap[?(@.estacaoId=='EST-01')].incidentes").value(2))
                .andExpect(jsonPath("$.heatmap[?(@.estacaoId=='EST-02')].incidentes").value(1));

        // Tenant novo: só o próprio incidente, atribuído à própria estação.
        mvc.perform(get("/api/v1/summary").session(outroGestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentesHoje").value(1))
                .andExpect(jsonPath("$.pendentes").value(1))
                .andExpect(jsonPath("$.heatmap.length()").value(1))
                .andExpect(jsonPath("$.heatmap[0].estacaoId").value("EST-01"))
                .andExpect(jsonPath("$.heatmap[0].incidentes").value(1));
    }

    @Test
    void heatmapRespeitaSinceEUntilQuandoInformados() throws Exception {
        // semear() já grava s1/s2/s3 "hoje" (CAM-01 x2, CAM-02 x1) e s4 "ontem" (CAM-01).
        // Pedindo desde ontem 00h ate agora, o heatmap de EST-01 passa a incluir s4.
        OffsetDateTime desde = OffsetDateTime.now().minusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        mvc.perform(get("/api/v1/summary")
                        .param("since", desde.toString())
                        .session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heatmap[?(@.estacaoId=='EST-01')].incidentes").value(3))
                .andExpect(jsonPath("$.heatmap[?(@.estacaoId=='EST-02')].incidentes").value(1));
    }

    @Test
    void semSinceNemUntilHeatmapContinuaSendoSoHoje() throws Exception {
        // Mesmo teste de sempre (heatmapAgregaPorEstacaoIncluindoAsSemIncidente),
        // reafirmado apos a Task 1 para provar que nada mudou por default.
        mvc.perform(get("/api/v1/summary").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heatmap[?(@.estacaoId=='EST-01')].incidentes").value(2))
                .andExpect(jsonPath("$.heatmap[?(@.estacaoId=='EST-02')].incidentes").value(1));
    }

    @Test
    void heatmapFiltraPorTurno() throws Exception {
        // O brief original gravava um unico incidente fixo (23h) e confiava
        // no default since/until (ate = agora) -- so passa se o teste rodar
        // entre 23h e meia-noite, porque "ate" corta qualquer coisa no
        // futuro. Aqui fixamos since/until para cobrir o dia inteiro e
        // usamos dois incidentes (turno N e turno A) para nao depender do
        // relogio real nem em qual hora o teste roda.
        repository.deleteAll();
        java.time.LocalDate hoje = java.time.LocalDate.now();
        java.time.ZoneId zona = java.time.ZoneId.systemDefault();
        repository.save(incidente("turno-n", "CAM-01", 3,
                hoje.atTime(3, 0).atZone(zona).toOffsetDateTime()));
        repository.saveAndFlush(incidente("turno-a", "CAM-01", 3,
                hoje.atTime(9, 0).atZone(zona).toOffsetDateTime()));

        mvc.perform(get("/api/v1/summary")
                        .param("since", hoje.atStartOfDay(zona).toOffsetDateTime().toString())
                        .param("until", hoje.atTime(23, 59, 59).atZone(zona).toOffsetDateTime().toString())
                        .param("turno", "N")
                        .session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heatmap[?(@.estacaoId=='EST-01')].incidentes").value(1));
    }

    @Test
    void turnoInvalidoDevolve400() throws Exception {
        mvc.perform(get("/api/v1/summary").param("turno", "X").session(gestor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void heatmapFiltraPorSetor() throws Exception {
        mvc.perform(get("/api/v1/summary").param("setor", "montagem").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heatmap[?(@.estacaoId=='EST-01')].incidentes").value(2))
                .andExpect(jsonPath("$.heatmap[?(@.estacaoId=='EST-02')].incidentes").value(0));
    }

    @Test
    void heatmapComSetorInexistenteFicaTodoZerado() throws Exception {
        mvc.perform(get("/api/v1/summary").param("setor", "setor-que-nao-existe").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heatmap[?(@.estacaoId=='EST-01')].incidentes").value(0))
                .andExpect(jsonPath("$.heatmap[?(@.estacaoId=='EST-02')].incidentes").value(0));
    }
}
