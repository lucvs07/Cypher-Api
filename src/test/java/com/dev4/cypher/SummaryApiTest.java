package com.dev4.cypher;

import org.springframework.jdbc.core.JdbcTemplate;
import com.dev4.cypher.model.EpiTipo;

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

    /**
     * Lacuna de cobertura carregada da revisao final de branch: nao havia
     * teste de isolamento de tenant para {@code historicoAnual}/{@code
     * distribuicaoTemporal} (campos atras de {@code detalhado=true}). Ambos
     * sao construidos a partir de {@code doTenant} (ja escopado por
     * empresaId), mas sem um teste dedicado essa garantia nunca foi provada
     * para esses dois campos especificamente.
     *
     * <p>Horarios fixos de "hoje" (em vez de agora.minusHours(N)) para que
     * as contagens sejam deterministicas em qualquer fuso/horario de
     * execucao.
     */
    @Test
    void detalhadoNaoVazaHistoricoAnualNemDistribuicaoTemporalDeOutroTenant() throws Exception {
        repository.deleteAll();
        java.time.LocalDate hoje = java.time.LocalDate.now();
        java.time.ZoneId zona = java.time.ZoneId.systemDefault();
        OffsetDateTime quando = hoje.atTime(9, 0).atZone(zona).toOffsetDateTime();
        int diaSemana = quando.atZoneSameInstant(zona).getDayOfWeek().getValue() - 1;
        int faixa2h = quando.atZoneSameInstant(zona).getHour() / 2;

        repository.saveAndFlush(incidente("detalhado-tenant-original", "CAM-01", 3, quando));

        MockHttpSession outroGestor = new MockHttpSession();
        mvc.perform(post("/api/v1/setup").session(outroGestor)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"admin\", \"password\": \"admin123\", "
                                + "\"empresaId\": \"outro-tenant-2\", \"empresaNome\": \"Outro Tenant\"}"))
                .andExpect(status().isOk());

        estacoes.saveAndFlush(new Estacao("outro-tenant-2:EST-01", "Estacao de outro tenant", "setor-b", true,
                "tok-outro-tenant-2-EST-01", "outro-tenant-2"));
        cameras.saveAndFlush(new Camera("outro-tenant-2:CAM-01", "Frontal de outro tenant",
                "outro-tenant-2:EST-01", "http://127.0.0.1:9099/stream", "rtsp://c", true,
                "key-outro-tenant-2-CAM-01", "outro-tenant-2"));

        Alerta incidenteOutroTenant = new Alerta();
        incidenteOutroTenant.setIncidentKey("outro-tenant-2:outro-detalhado-1:run-s:1:20260810T000000000Z");
        incidenteOutroTenant.setRunId("run-s");
        incidenteOutroTenant.setTrackId(1);
        incidenteOutroTenant.setCameraId("outro-tenant-2:CAM-01");
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
        incidenteOutroTenant.setDataHora(quando);
        incidenteOutroTenant.setStatus(AlertaStatus.PENDENTE);
        incidenteOutroTenant.setEmpresaId("outro-tenant-2");
        repository.saveAndFlush(incidenteOutroTenant);

        // Tenant original: so o proprio incidente aparece, nada do outro tenant.
        mvc.perform(get("/api/v1/summary")
                        .param("since", hoje.atStartOfDay(zona).toOffsetDateTime().toString())
                        .param("until", hoje.atTime(23, 59, 59).atZone(zona).toOffsetDateTime().toString())
                        .param("detalhado", "true")
                        .session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historicoAnual[?(@.dia=='" + hoje + "')].incidentes").value(1))
                .andExpect(jsonPath("$.distribuicaoTemporal[?(@.diaSemana==" + diaSemana
                        + " && @.faixa2h==" + faixa2h + ")].incidentes").value(1));

        // Tenant novo: idem, so o proprio incidente -- nada do tenant original vaza.
        mvc.perform(get("/api/v1/summary")
                        .param("since", hoje.atStartOfDay(zona).toOffsetDateTime().toString())
                        .param("until", hoje.atTime(23, 59, 59).atZone(zona).toOffsetDateTime().toString())
                        .param("detalhado", "true")
                        .session(outroGestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historicoAnual[?(@.dia=='" + hoje + "')].incidentes").value(1))
                .andExpect(jsonPath("$.distribuicaoTemporal[?(@.diaSemana==" + diaSemana
                        + " && @.faixa2h==" + faixa2h + ")].incidentes").value(1));
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
        //
        // Reseeded com horarios fixos de "hoje" (em vez de agora.minusHours(N)
        // herdado do semear() do setUp()): perto da meia-noite, "agora menos
        // N horas" pode cair no dia calendario anterior e ficar fora da
        // janela padrao (hoje 00h -> agora), fazendo o teste falhar de forma
        // intermitente. Mesmo padrao ja usado em heatmapFiltraPorTurno/heatmapFiltraPorEpi.
        // Ancorado no inicio do dia calendario ("hoje 00h") em vez de
        // agora.minusHours(N): isso permanece dentro da janela padrao
        // (desde = hoje 00h, ate = agora) em qualquer horario em que o
        // teste rode, sem precisar de since/until explicitos -- o que
        // preserva a intencao do teste (defaults so trazem "hoje").
        repository.deleteAll();
        java.time.LocalDate hoje = java.time.LocalDate.now();
        java.time.ZoneId zona = java.time.ZoneId.systemDefault();
        OffsetDateTime inicioDoDia = hoje.atStartOfDay(zona).toOffsetDateTime();
        repository.save(incidente("hoje-1", "CAM-01", 3, inicioDoDia));
        repository.save(incidente("hoje-2", "CAM-01", 3, inicioDoDia));
        repository.saveAndFlush(incidente("hoje-3", "CAM-02", 3, inicioDoDia));

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
        // Mesmo cuidado de heatmapFiltraPorTurno: reseed com horarios fixos
        // de "hoje" e since/until explicitos cobrindo o dia inteiro, para
        // nao depender de agora.minusHours(N) do semear() do setUp() (que
        // pode cair no dia calendario anterior perto da meia-noite).
        repository.deleteAll();
        java.time.LocalDate hoje = java.time.LocalDate.now();
        java.time.ZoneId zona = java.time.ZoneId.systemDefault();
        repository.save(incidente("setor-1", "CAM-01", 3, hoje.atTime(9, 0).atZone(zona).toOffsetDateTime()));
        repository.save(incidente("setor-2", "CAM-01", 3, hoje.atTime(10, 0).atZone(zona).toOffsetDateTime()));
        repository.saveAndFlush(incidente("setor-3", "CAM-02", 3, hoje.atTime(11, 0).atZone(zona).toOffsetDateTime()));

        mvc.perform(get("/api/v1/summary")
                        .param("since", hoje.atStartOfDay(zona).toOffsetDateTime().toString())
                        .param("until", hoje.atTime(23, 59, 59).atZone(zona).toOffsetDateTime().toString())
                        .param("setor", "montagem")
                        .session(gestor))
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

    /**
     * Achado real da revisao final de branch: {@code distribuicaoTemporal} e
     * {@code historicoAnual} sao agregacoes TEMPORAIS (nao espaciais), mas
     * herdavam do heatmap espacial o gate {@code estacaoId == null ->
     * continue}. Isso descartava silenciosamente incidentes de cameras
     * desativadas DEPOIS do fato (a camera some de {@code CatalogService
     * .stations()}, que so lista ativas — o {@code estacaoId} correspondente
     * fica null), deixando a resposta internamente contraditoria: o
     * incidente contava em {@code ultimosSeteDias}, mas sumia de {@code
     * historicoAnual}/{@code distribuicaoTemporal}. O lookup de estacao so e
     * necessario quando {@code setor != null}; sem filtro de setor, o
     * incidente deve continuar contando nesses dois agregados.
     */
    @Test
    void distribuicaoTemporalEHistoricoAnualIncluemIncidenteDeCameraDesativadaDepois() throws Exception {
        repository.deleteAll();
        java.time.LocalDate hoje = java.time.LocalDate.now();
        java.time.ZoneId zona = java.time.ZoneId.systemDefault();
        OffsetDateTime quando = hoje.atTime(9, 0).atZone(zona).toOffsetDateTime();
        int diaSemana = quando.atZoneSameInstant(zona).getDayOfWeek().getValue() - 1;
        int faixa2h = quando.atZoneSameInstant(zona).getHour() / 2;

        cameras.saveAndFlush(new Camera("acme-test:CAM-03", "Camera a ser desativada", "acme-test:EST-01",
                "http://127.0.0.1:9092/stream", "rtsp://d", true,
                "key-acme-test-CAM-03", AuthTestSupport.EMPRESA_ID));
        repository.save(incidente("camera-desativada-1", "CAM-03", 3, quando));
        // Incidente de controle numa camera ATIVA, mesmo bucket temporal e
        // mesmo setor (montagem/EST-01): sem ele, a assercao com
        // setor=montagem passaria mesmo que o filtro de setor descartasse
        // TUDO incondicionalmente -- nao provaria que so o incidente
        // irresolvivel foi excluido.
        repository.saveAndFlush(incidente("camera-ativa-1", "CAM-01", 3, quando));

        // Desativa a CAM-03 DEPOIS de o incidente ja estar gravado -- e o
        // cenario real (o alerta foi lancado antes de a camera sair de
        // operacao). CatalogService.stations() so lista cameras ativas, entao
        // estacaoPorCamera.get("CAM-03") passa a devolver null.
        Camera cam03 = cameras.findById("acme-test:CAM-03").orElseThrow();
        cam03.setAtiva(false);
        cameras.saveAndFlush(cam03);

        String desde = hoje.atStartOfDay(zona).toOffsetDateTime().toString();
        String ate = hoje.atTime(23, 59, 59).atZone(zona).toOffsetDateTime().toString();

        // Sem filtro de setor: os dois incidentes contam -- o da camera
        // desativada nao pode ser descartado so por a estacao nao ser mais
        // resolvivel.
        mvc.perform(get("/api/v1/summary")
                        .param("since", desde).param("until", ate)
                        .param("detalhado", "true")
                        .session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historicoAnual[?(@.dia=='" + hoje + "')].incidentes").value(2))
                .andExpect(jsonPath("$.distribuicaoTemporal[?(@.diaSemana==" + diaSemana
                        + " && @.faixa2h==" + faixa2h + ")].incidentes").value(2));

        // Com filtro de setor: so o incidente da camera ATIVA (EST-01 e
        // montagem) continua contando -- o da camera desativada, cuja
        // estacao nao pode mais ser resolvida, e corretamente excluido. Se
        // esse "1" caisse pra "0", o filtro de setor estaria descartando tudo
        // incondicionalmente em vez de respeitar apenas quem nao resolve.
        mvc.perform(get("/api/v1/summary")
                        .param("since", desde).param("until", ate)
                        .param("detalhado", "true")
                        .param("setor", "montagem")
                        .session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historicoAnual[?(@.dia=='" + hoje + "')].incidentes").value(1))
                .andExpect(jsonPath("$.distribuicaoTemporal[?(@.diaSemana==" + diaSemana
                        + " && @.faixa2h==" + faixa2h + ")].incidentes").value(1));
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void heatmapFiltraPorEpiTambemEmIncidenteGravadoNoFormatoLegado() throws Exception {
        // Incidente de antes do contrato EpiTipo: epiMissing gravado como no_helmet.
        Alerta legado = repository.saveAndFlush(incidente("epi-legado", "CAM-01", 2, OffsetDateTime.now()));
        jdbc.update("insert into alerta_epi_missing (alerta_id, epi) values (?, 'no_helmet')", legado.getId());

        mvc.perform(get("/api/v1/summary").param("epi", "helmet").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heatmap[?(@.estacaoId=='EST-01')].incidentes").value(1));
    }

    @Test
    void heatmapFiltraPorEpi() throws Exception {
        Alerta semCapacete = incidente("epi-1", "CAM-01", 2, OffsetDateTime.now());
        semCapacete.setEpiMissing(List.of(EpiTipo.HELMET));
        repository.saveAndFlush(semCapacete);

        mvc.perform(get("/api/v1/summary").param("epi", "HELMET").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heatmap[?(@.estacaoId=='EST-01')].incidentes").value(1))
                .andExpect(jsonPath("$.heatmap[?(@.estacaoId=='EST-02')].incidentes").value(0));
    }

    @Test
    void epiForaDoEnumDevolve400() throws Exception {
        // Chave em portugues do filtro antigo (capacete/oculos/...) tambem e invalida.
        mvc.perform(get("/api/v1/summary").param("epi", "capacete").session(gestor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void distribuicaoTemporalAgregaPorDiaDaSemanaEFaixaDe2h() throws Exception {
        // Isola do semear() do setUp(): a data fixa abaixo (2026-09-09, uma
        // quarta) e literalmente "hoje" em alguns ambientes de CI, e os
        // incidentes gravados via agora.minusHours(N) cairiam no mesmo
        // bucket dia-da-semana x faixa2h que este teste verifica, inflando
        // a contagem esperada. Mesmo padrao ja usado em heatmapFiltraPorTurno.
        repository.deleteAll();
        // Quarta-feira (diaSemana=2, 0=Seg) as 23h -> faixa2h = 23/2 = 11.
        java.time.LocalDate quartaConhecida = java.time.LocalDate.of(2026, 9, 9); // e uma quarta
        assert quartaConhecida.getDayOfWeek() == java.time.DayOfWeek.WEDNESDAY;
        OffsetDateTime as23h = quartaConhecida.atTime(23, 0)
                .atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime();
        repository.saveAndFlush(incidente("temporal-1", "CAM-01", 2, as23h));

        mvc.perform(get("/api/v1/summary")
                        .param("since", quartaConhecida.atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().toString())
                        .param("until", quartaConhecida.plusDays(1).atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().toString())
                        .param("detalhado", "true")
                        .session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distribuicaoTemporal[?(@.diaSemana==2 && @.faixa2h==11)].incidentes").value(1));
    }

    @Test
    void semDetalhadoDistribuicaoTemporalVemNula() throws Exception {
        mvc.perform(get("/api/v1/summary").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distribuicaoTemporal").doesNotExist());
    }

    @Test
    void historicoAnualTemUmaLinhaPorDiaDoAnoCorrenteEIgnoraSinceUntil() throws Exception {
        int anoCorrente = java.time.LocalDate.now().getYear();
        java.time.LocalDate diaNoAno = java.time.LocalDate.of(anoCorrente, 3, 15);
        repository.saveAndFlush(incidente("anual-1", "CAM-01", 2,
                diaNoAno.atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime()));

        // since/until aqui apontam pra HOJE -- historicoAnual deve ignorar e
        // trazer o ano inteiro (incluindo 15/mar, fora dessa janela).
        mvc.perform(get("/api/v1/summary")
                        .param("since", OffsetDateTime.now().toString())
                        .param("detalhado", "true")
                        .session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historicoAnual.length()")
                        .value(anoCorrente % 4 == 0 && (anoCorrente % 100 != 0 || anoCorrente % 400 == 0) ? 366 : 365))
                .andExpect(jsonPath("$.historicoAnual[?(@.dia=='" + diaNoAno + "')].incidentes").value(1));
    }

    @Test
    void semDetalhadoHistoricoAnualVemNulo() throws Exception {
        mvc.perform(get("/api/v1/summary").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historicoAnual").doesNotExist());
    }
}
