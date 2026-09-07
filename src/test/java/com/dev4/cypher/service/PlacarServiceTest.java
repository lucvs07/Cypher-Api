package com.dev4.cypher.service;

import com.dev4.cypher.dto.PlacarEquipeResponse;
import com.dev4.cypher.dto.TeamRequest;
import com.dev4.cypher.model.MonitoramentoDiario;
import com.dev4.cypher.model.Turno;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
    }

    private void autenticarComo(String empresaId) {
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new GestorAuthentication("gestor@" + empresaId, empresaId));
        SecurityContextHolder.setContext(ctx);
    }
}
