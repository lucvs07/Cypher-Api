package com.dev4.cypher.service;

import com.dev4.cypher.dto.PlacarEquipeResponse;
import com.dev4.cypher.dto.TeamRequest;
import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.model.AlertaNivel;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.model.AlertaTipo;
import com.dev4.cypher.model.MonitoramentoDiario;
import com.dev4.cypher.model.Turno;
import com.dev4.cypher.repository.AlertaRepository;
import com.dev4.cypher.repository.MonitoramentoDiarioRepository;
import com.dev4.cypher.security.GestorAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Transactional
class PlacarServiceTest {

    @Autowired
    private PlacarService service;

    @Autowired
    private EquipeService equipeService;

    @Autowired
    private com.dev4.cypher.repository.EstacaoRepository estacoes;

    @Autowired
    private com.dev4.cypher.repository.CameraRepository cameras;

    @Autowired
    private MonitoramentoDiarioRepository registros;

    @Autowired
    private AlertaRepository alertas;

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void conformidadeEhUmMenosARazaoEntreIncidenteEMonitorado() {
        autenticarComo("demo");
        estacoes.saveAndFlush(new com.dev4.cypher.model.Estacao("demo:EST-PL1", "E", "s", true, "tok", "demo"));
        cameras.saveAndFlush(new com.dev4.cypher.model.Camera("demo:CAM-PL1", "C", "demo:EST-PL1",
                "http://x/stream", null, true, "key", "demo"));
        equipeService.criar(new TeamRequest("EQ-PL1", "Equipe 1", Turno.MANHA, List.of("EST-PL1"), true,
                null, null, null));

        LocalDate hoje = LocalDate.now();
        registros.saveAndFlush(new MonitoramentoDiario(null, "demo:CAM-PL1", "demo", hoje, Turno.MANHA,
                201600, OffsetDateTime.now()));

        List<PlacarEquipeResponse> placar = service.placar(
                OffsetDateTime.now().minusDays(7), OffsetDateTime.now());

        PlacarEquipeResponse eq1 = placar.stream().filter(p -> p.equipeId().equals("EQ-PL1")).findFirst().orElseThrow();
        assertThat(eq1.dadosSuficientes()).isTrue();
        assertThat(eq1.conformidade()).isEqualTo(1.0); // sem incidente => conformidade total
    }

    @Test
    void semanaAnteriorSemMonitoramentoDeixaDeltaNuloMasMantemConformidade() {
        autenticarComo("demo");
        estacoes.saveAndFlush(new com.dev4.cypher.model.Estacao("demo:EST-DL", "E", "s", true, "tokdl", "demo"));
        cameras.saveAndFlush(new com.dev4.cypher.model.Camera("demo:CAM-DL", "C", "demo:EST-DL",
                "http://dl/stream", null, true, "keydl", "demo"));
        equipeService.criar(new TeamRequest("EQ-DL", "Equipe 1", Turno.MANHA, List.of("EST-DL"), true,
                null, null, null));

        // Monitoramento só na janela atual (hoje); nada na semana anterior.
        registros.saveAndFlush(new MonitoramentoDiario(null, "demo:CAM-DL", "demo", LocalDate.now(), Turno.MANHA,
                230400, OffsetDateTime.now()));

        var eq = service.placar(OffsetDateTime.now().minusDays(7), OffsetDateTime.now())
                .stream().filter(p -> p.equipeId().equals("EQ-DL")).findFirst().orElseThrow();

        assertThat(eq.dadosSuficientes()).isTrue();
        assertThat(eq.conformidade()).isNotNull();
        assertThat(eq.deltaConformidadeSemana()).isNull();
    }

    @Test
    void equipeSemCameraAtivaAindaAparecePlacarComZeros() {
        autenticarComo("demo");
        estacoes.saveAndFlush(new com.dev4.cypher.model.Estacao("demo:EST-PL2", "E", "s", true, "tok2", "demo"));
        equipeService.criar(new TeamRequest("EQ-PL2", "Equipe vazia", Turno.TARDE, List.of("EST-PL2"), true,
                null, null, null));

        List<PlacarEquipeResponse> placar = service.placar(
                OffsetDateTime.now().minusDays(7), OffsetDateTime.now());

        PlacarEquipeResponse eq2 = placar.stream().filter(p -> p.equipeId().equals("EQ-PL2")).findFirst().orElseThrow();
        assertThat(eq2.dadosSuficientes()).isFalse();
        assertThat(eq2.conformidade()).isNull();
        assertThat(eq2.cobertura()).isEqualTo(0.0);
        assertThat(eq2.sequenciaAtual()).isZero();
    }

    @Test
    void rankingColocaEquipesComDadosSuficientesAntesDasSemDados() {
        autenticarComo("demo");
        // Equipe com dados suficientes: estacao + camera ativa + monitoramento no turno.
        estacoes.saveAndFlush(new com.dev4.cypher.model.Estacao("demo:EST-RK1", "E", "s", true, "tokrk1", "demo"));
        cameras.saveAndFlush(new com.dev4.cypher.model.Camera("demo:CAM-RK1", "C", "demo:EST-RK1",
                "http://rk1/stream", null, true, "keyrk1", "demo"));
        equipeService.criar(new TeamRequest("EQ-RK1", "Com dados", Turno.MANHA, List.of("EST-RK1"), true,
                null, null, null));
        registros.saveAndFlush(new MonitoramentoDiario(null, "demo:CAM-RK1", "demo", LocalDate.now(), Turno.MANHA,
                201600, OffsetDateTime.now()));

        // Equipe sem dados: estacao sem camera.
        estacoes.saveAndFlush(new com.dev4.cypher.model.Estacao("demo:EST-RK2", "E", "s", true, "tokrk2", "demo"));
        equipeService.criar(new TeamRequest("EQ-RK2", "Sem dados", Turno.TARDE, List.of("EST-RK2"), true,
                null, null, null));

        List<PlacarEquipeResponse> placar = service.placar(
                OffsetDateTime.now().minusDays(7), OffsetDateTime.now());

        int ultimoComDados = -1;
        int primeiroSemDados = Integer.MAX_VALUE;
        for (int i = 0; i < placar.size(); i++) {
            if (placar.get(i).dadosSuficientes()) {
                ultimoComDados = i;
            } else if (primeiroSemDados == Integer.MAX_VALUE) {
                primeiroSemDados = i;
            }
        }
        assertThat(ultimoComDados).isLessThan(primeiroSemDados);
    }

    @Test
    void coberturaAbaixoDoPisoMarcaDadosInsuficientesEFicaForaDoRanking() {
        autenticarComo("demo");
        estacoes.saveAndFlush(new com.dev4.cypher.model.Estacao("demo:EST-DI", "E", "s", true, "tokdi", "demo"));
        cameras.saveAndFlush(new com.dev4.cypher.model.Camera("demo:CAM-DI", "C", "demo:EST-DI",
                "http://x/stream", null, true, "keydi", "demo"));
        equipeService.criar(new TeamRequest("EQ-DI", "Equipe pouco monitorada", Turno.MANHA, List.of("EST-DI"),
                true, null, null, null));
        // 1h monitorada numa janela de 7 dias => cobertura ~0,005, bem abaixo de 0,5
        registros.saveAndFlush(new MonitoramentoDiario(null, "demo:CAM-DI", "demo", LocalDate.now(), Turno.MANHA,
                3600, OffsetDateTime.now()));

        var eq = service.placar(OffsetDateTime.now().minusDays(7), OffsetDateTime.now())
                .stream().filter(p -> p.equipeId().equals("EQ-DI")).findFirst().orElseThrow();

        assertThat(eq.dadosSuficientes()).isFalse();
        assertThat(eq.conformidade()).isNull();
        assertThat(eq.deltaConformidadeSemana()).isNull();
        // Sem monitoramento confiável a sequência de 90 dias não pode ser exibida.
        assertThat(eq.sequenciaAtual()).isZero();
        assertThat(eq.melhorSequencia()).isZero();
    }

    @Test
    void deltaSemanalNaoNuloEPositivoQuandoSemanaAnteriorTeveMaisIncidente() {
        autenticarComo("demo");
        LocalDate hoje = LocalDate.now();
        OffsetDateTime agora = hoje.atTime(12, 0).atOffset(ZoneOffset.UTC);
        estacoes.saveAndFlush(new com.dev4.cypher.model.Estacao("demo:EST-DP", "E", "s", true, "tokdp", "demo"));
        cameras.saveAndFlush(new com.dev4.cypher.model.Camera("demo:CAM-DP", "C", "demo:EST-DP",
                "http://dp/stream", null, true, "keydp", "demo"));
        equipeService.criar(new TeamRequest("EQ-DP", "Equipe delta", Turno.MANHA, List.of("EST-DP"), true,
                null, null, null));

        // Cobertura >= piso nas DUAS janelas: atual [hoje-7 .. hoje], anterior [hoje-14 .. hoje-8].
        registros.saveAndFlush(new MonitoramentoDiario(null, "demo:CAM-DP", "demo", hoje, Turno.MANHA,
                200000, agora));
        registros.saveAndFlush(new MonitoramentoDiario(null, "demo:CAM-DP", "demo", hoje.minusDays(10), Turno.MANHA,
                200000, agora));

        // Incidente só na semana anterior => conformidade anterior < atual => delta > 0.
        alertas.saveAndFlush(fallRisk("demo:CAM-DP", hoje.minusDays(10).atTime(8, 0).atOffset(ZoneOffset.UTC),
                100.0, 2));

        var eq = service.placar(agora.minusDays(7), agora)
                .stream().filter(p -> p.equipeId().equals("EQ-DP")).findFirst().orElseThrow();

        assertThat(eq.dadosSuficientes()).isTrue();
        assertThat(eq.deltaConformidadeSemana()).isNotNull();
        assertThat(eq.deltaConformidadeSemana()).isGreaterThan(0.0);
    }

    @Test
    void conformidadeCaiComADuracaoDoIncidente() {
        autenticarComo("demo");
        LocalDate hoje = LocalDate.now();
        OffsetDateTime agora = hoje.atTime(12, 0).atOffset(ZoneOffset.UTC);
        estacoes.saveAndFlush(new com.dev4.cypher.model.Estacao("demo:EST-CN", "E", "s", true, "tokcn", "demo"));
        cameras.saveAndFlush(new com.dev4.cypher.model.Camera("demo:CAM-CN", "C", "demo:EST-CN",
                "http://cn/stream", null, true, "keycn", "demo"));
        equipeService.criar(new TeamRequest("EQ-CN", "Equipe conf", Turno.MANHA, List.of("EST-CN"), true,
                null, null, null));

        long segundosMonitorados = 200000;
        registros.saveAndFlush(new MonitoramentoDiario(null, "demo:CAM-CN", "demo", hoje, Turno.MANHA,
                segundosMonitorados, agora));
        alertas.saveAndFlush(fallRisk("demo:CAM-CN", hoje.atTime(8, 0).atOffset(ZoneOffset.UTC), 5000.0, 2));

        var eq = service.placar(agora.minusDays(7), agora)
                .stream().filter(p -> p.equipeId().equals("EQ-CN")).findFirst().orElseThrow();

        assertThat(eq.conformidade()).isCloseTo(1.0 - 5000.0 / segundosMonitorados, within(1e-6));
    }

    @Test
    void fallRiskLongoQuebraSequenciaMasCurtoNao() {
        autenticarComo("demo");
        LocalDate hoje = LocalDate.now();
        OffsetDateTime agora = hoje.atTime(12, 0).atOffset(ZoneOffset.UTC);

        // Equipe A: FALL_RISK de 5s tres dias atras => quebra a sequencia atual.
        estacoes.saveAndFlush(new com.dev4.cypher.model.Estacao("demo:EST-SL", "E", "s", true, "toksl", "demo"));
        cameras.saveAndFlush(new com.dev4.cypher.model.Camera("demo:CAM-SL", "C", "demo:EST-SL",
                "http://sl/stream", null, true, "keysl", "demo"));
        equipeService.criar(new TeamRequest("EQ-SL", "Equipe seq longa", Turno.MANHA, List.of("EST-SL"), true,
                null, null, null));
        registros.saveAndFlush(new MonitoramentoDiario(null, "demo:CAM-SL", "demo", hoje, Turno.MANHA,
                200000, agora));
        alertas.saveAndFlush(fallRisk("demo:CAM-SL", hoje.minusDays(3).atTime(8, 0).atOffset(ZoneOffset.UTC),
                5.0, 2));

        // Equipe B: FALL_RISK de 1s (< DURACAO_MINIMA_QUEBRA_SEGUNDOS) => NAO quebra.
        estacoes.saveAndFlush(new com.dev4.cypher.model.Estacao("demo:EST-SC", "E", "s", true, "toksc", "demo"));
        cameras.saveAndFlush(new com.dev4.cypher.model.Camera("demo:CAM-SC", "C", "demo:EST-SC",
                "http://sc/stream", null, true, "keysc", "demo"));
        equipeService.criar(new TeamRequest("EQ-SC", "Equipe seq curta", Turno.MANHA, List.of("EST-SC"), true,
                null, null, null));
        registros.saveAndFlush(new MonitoramentoDiario(null, "demo:CAM-SC", "demo", hoje, Turno.MANHA,
                200000, agora));
        alertas.saveAndFlush(fallRisk("demo:CAM-SC", hoje.minusDays(3).atTime(8, 0).atOffset(ZoneOffset.UTC),
                1.0, 2));

        var placar = service.placar(agora.minusDays(7), agora);
        var eqLongo = placar.stream().filter(p -> p.equipeId().equals("EQ-SL")).findFirst().orElseThrow();
        var eqCurto = placar.stream().filter(p -> p.equipeId().equals("EQ-SC")).findFirst().orElseThrow();

        assertThat(eqLongo.sequenciaAtual()).isEqualTo(3);
        assertThat(eqCurto.sequenciaAtual()).isEqualTo(90);
    }

    @Test
    void metaZeroSeveridade4NaoEhAtingidaComIncidenteSeveridade4NoTurno() {
        autenticarComo("demo");
        LocalDate hoje = LocalDate.now();
        OffsetDateTime agora = hoje.atTime(12, 0).atOffset(ZoneOffset.UTC);
        estacoes.saveAndFlush(new com.dev4.cypher.model.Estacao("demo:EST-S4", "E", "s", true, "toks4", "demo"));
        cameras.saveAndFlush(new com.dev4.cypher.model.Camera("demo:CAM-S4", "C", "demo:EST-S4",
                "http://s4/stream", null, true, "keys4", "demo"));
        equipeService.criar(new TeamRequest("EQ-S4", "Equipe meta", Turno.MANHA, List.of("EST-S4"), true,
                null, true, null));
        registros.saveAndFlush(new MonitoramentoDiario(null, "demo:CAM-S4", "demo", hoje, Turno.MANHA,
                200000, agora));
        alertas.saveAndFlush(fallRisk("demo:CAM-S4", hoje.atTime(8, 0).atOffset(ZoneOffset.UTC), 6.0, 4));

        var eq = service.placar(agora.minusDays(7), agora)
                .stream().filter(p -> p.equipeId().equals("EQ-S4")).findFirst().orElseThrow();

        assertThat(eq.dadosSuficientes()).isTrue();
        assertThat(eq.metaZeroSeveridade4Atingida()).isFalse();
    }

    private Alerta fallRisk(String cameraId, OffsetDateTime dataHora, Double duracaoSegundos, int severidade) {
        Alerta a = new Alerta();
        a.setIncidentKey(cameraId + ":" + UUID.randomUUID());
        a.setRunId("run-teste");
        a.setTrackId(1);
        a.setCameraId(cameraId);
        a.setTipo(AlertaTipo.FALL_RISK);
        a.setSeveridade(severidade);
        a.setNivel(AlertaNivel.fromSeverity(severidade));
        a.setDescricao("Queda detectada");
        a.setReason("fallen_sustained");
        a.setPosturaCategoria("immediate_risk");
        a.setRebaScore(11);
        a.setActionLevel(4);
        a.setZoneStatus("outside");
        a.setDataHora(dataHora);
        a.setDuracaoSegundos(duracaoSegundos);
        a.setStatus(AlertaStatus.PENDENTE);
        a.setEmpresaId("demo");
        return a;
    }

    private void autenticarComo(String empresaId) {
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new GestorAuthentication("gestor@" + empresaId, empresaId));
        SecurityContextHolder.setContext(ctx);
    }
}
