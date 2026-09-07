package com.dev4.cypher.service;

import com.dev4.cypher.dto.PlacarEquipeResponse;
import com.dev4.cypher.model.*;
import com.dev4.cypher.repository.AlertaRepository;
import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.repository.EquipeRepository;
import com.dev4.cypher.repository.MonitoramentoDiarioRepository;
import com.dev4.cypher.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Placar de conformidade por equipe (spec §3.2), agregado em memória no
 * mesmo espírito de `SummaryService` — mas escopado por `empresaId` desde a
 * primeira linha (diferente de `SummaryService`, que não está — ver Global
 * Constraints do plano).
 */
@Service
public class PlacarService {

    private static final int JANELA_SEQUENCIA_DIAS = 90;
    private static final double PISO_COBERTURA = 0.5;                    // spec §3.2 — abaixo disso: "dados insuficientes"
    private static final double DURACAO_MINIMA_QUEBRA_SEGUNDOS = 3.0;    // spec §3.2/§7 — FALL_RISK mais curto (ou aberto) não quebra a sequência

    private final EquipeRepository equipes;
    private final CameraRepository cameras;
    private final AlertaRepository alertas;
    private final MonitoramentoDiarioRepository monitoramentos;

    public PlacarService(EquipeRepository equipes, CameraRepository cameras,
                         AlertaRepository alertas, MonitoramentoDiarioRepository monitoramentos) {
        this.equipes = equipes;
        this.cameras = cameras;
        this.alertas = alertas;
        this.monitoramentos = monitoramentos;
    }

    @Transactional(readOnly = true)
    public List<PlacarEquipeResponse> placar(OffsetDateTime since, OffsetDateTime until) {
        String empresaId = TenantContext.currentEmpresaId();
        OffsetDateTime semanaAnteriorInicio = since.minusDays(7);

        List<PlacarEquipeResponse> resultado = new ArrayList<>();
        for (Equipe equipe : equipes.findByEmpresaIdAndAtivaTrue(empresaId)) {
            List<String> cameraIds = camerasDaEquipe(equipe);

            Turno turno = equipe.getTurno();
            double cobertura = cobertura(empresaId, cameraIds, turno, since, until);
            int[] sequencias = sequencias(empresaId, cameraIds, until.toLocalDate());
            boolean dadosSuficientes = cobertura >= PISO_COBERTURA;

            Double conformidadeAtual = null;
            Double delta = null;
            boolean metaConformidadeAtingida = false;
            if (dadosSuficientes) {
                double atual = conformidade(empresaId, cameraIds, turno, since, until);
                conformidadeAtual = atual;
                // Delta week-over-week só é significativo quando a semana anterior
                // também tem cobertura suficiente — senão `anterior` cairia para
                // 0.0 (sem MonitoramentoDiario) e o ranking colapsaria para
                // conformidade absoluta, violando "SEMPRE delta, NUNCA absoluta".
                double coberturaAnterior = cobertura(empresaId, cameraIds, turno, semanaAnteriorInicio, since);
                if (coberturaAnterior >= PISO_COBERTURA) {
                    double anterior = conformidade(empresaId, cameraIds, turno, semanaAnteriorInicio, since);
                    delta = atual - anterior;
                }
                metaConformidadeAtingida = equipe.getMetaConformidade() != null
                        && atual >= equipe.getMetaConformidade();
            }

            // Só afirma "meta atingida" com dados suficientes — senão uma equipe
            // sem monitoramento reportaria zero-severidade-4 como conquista.
            boolean metaZeroSeveridade4Atingida = dadosSuficientes
                    && Boolean.TRUE.equals(equipe.getMetaZeroSeveridade4Ativa())
                    && semSeveridade4(empresaId, cameraIds, turno, since, until);

            resultado.add(new PlacarEquipeResponse(
                    TenantContext.codigo(equipe.getId()), equipe.getNome(), equipe.getTurno(),
                    dadosSuficientes, conformidadeAtual, cobertura, sequencias[0], sequencias[1],
                    equipe.getMetaConformidade(), metaConformidadeAtingida,
                    equipe.getMetaZeroSeveridade4Ativa(), metaZeroSeveridade4Atingida,
                    equipe.getMetaTempoReconhecimentoSegundos(), delta));
        }

        // Ranking por delta decrescente entre as equipes com dados suficientes;
        // "dados insuficientes" nunca sobem no ranking (spec §3.2) — vão pro fim,
        // ordem estável.
        resultado.sort(Comparator
                .comparing((PlacarEquipeResponse p) -> p.dadosSuficientes()).reversed()
                .thenComparing(p -> p.deltaConformidadeSemana() == null ? Double.NEGATIVE_INFINITY
                        : p.deltaConformidadeSemana(), Comparator.reverseOrder()));
        return resultado;
    }

    private List<String> camerasDaEquipe(Equipe equipe) {
        List<String> resultado = new ArrayList<>();
        for (String estacaoId : equipe.getEstacaoIds()) {
            cameras.findByEstacaoIdAndAtivaTrue(estacaoId).forEach(c -> resultado.add(c.getId()));
        }
        return resultado;
    }

    /** Incidentes das câmeras da equipe cujo detectedAt cai na janela do turno da equipe (spec §3.2). */
    private List<Alerta> incidentesDoTurno(String empresaId, List<String> cameraIds, Turno turno,
                                           OffsetDateTime inicio, OffsetDateTime fim) {
        if (cameraIds.isEmpty()) {
            return List.of();
        }
        return alertas.findByEmpresaIdAndCameraIdInAndDataHoraBetween(empresaId, cameraIds, inicio, fim)
                .stream().filter(a -> TurnoWindow.turnoDe(a.getDataHora()) == turno).toList();
    }

    private double conformidade(String empresaId, List<String> cameraIds, Turno turno,
                                OffsetDateTime inicio, OffsetDateTime fim) {
        if (cameraIds.isEmpty()) {
            return 0.0;
        }
        double segundosIncidente = incidentesDoTurno(empresaId, cameraIds, turno, inicio, fim)
                .stream().mapToDouble(a -> a.getDuracaoSegundos() == null ? 0.0 : a.getDuracaoSegundos()).sum();
        double segundosMonitorados = somaMonitorado(empresaId, cameraIds, turno, inicio, fim);
        if (segundosMonitorados <= 0) {
            return 0.0;
        }
        return Math.max(0.0, 1.0 - (segundosIncidente / segundosMonitorados));
    }

    private double cobertura(String empresaId, List<String> cameraIds, Turno turno,
                             OffsetDateTime inicio, OffsetDateTime fim) {
        if (cameraIds.isEmpty()) {
            return 0.0;
        }
        // +1 porque `findBy...AndDiaBetween` é inclusivo nas duas pontas: uma
        // janela de 7 dias corridos cobre 7 datas, não 6.
        long dias = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(
                inicio.toLocalDate(), fim.toLocalDate()) + 1);
        double segundosMonitorados = somaMonitorado(empresaId, cameraIds, turno, inicio, fim);
        // Normalizado pelo nº de câmeras e restrito ao turno da equipe: sem isso,
        // equipe de N câmeras 24h marcaria até N×300% (spec §3.2/§2.3).
        return segundosMonitorados / (TurnoWindow.DURACAO_NOMINAL_SEGUNDOS * dias * cameraIds.size());
    }

    private double somaMonitorado(String empresaId, List<String> cameraIds, Turno turno,
                                  OffsetDateTime inicio, OffsetDateTime fim) {
        if (cameraIds.isEmpty()) {
            return 0.0;
        }
        return monitoramentos.findByEmpresaIdAndCameraIdInAndTurnoAndDiaBetween(
                        empresaId, cameraIds, turno, inicio.toLocalDate(), fim.toLocalDate())
                .stream().mapToLong(MonitoramentoDiario::getSegundosMonitorados).sum();
    }

    private boolean semSeveridade4(String empresaId, List<String> cameraIds, Turno turno,
                                   OffsetDateTime inicio, OffsetDateTime fim) {
        if (cameraIds.isEmpty()) {
            return true;
        }
        return incidentesDoTurno(empresaId, cameraIds, turno, inicio, fim)
                .stream().noneMatch(a -> a.getSeveridade() != null && a.getSeveridade() == 4);
    }

    /** @return [sequenciaAtual, melhorSequencia] — dias consecutivos sem FALL_RISK, janela de 90 dias (spec §3.2). */
    private int[] sequencias(String empresaId, List<String> cameraIds, LocalDate hoje) {
        if (cameraIds.isEmpty()) {
            return new int[]{0, 0};
        }
        // -(N-1): a janela cobre exatamente N datas contando `hoje`.
        LocalDate limiteDia = hoje.minusDays(JANELA_SEQUENCIA_DIAS - 1L);
        OffsetDateTime limiteInstante = limiteDia.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();

        Set<LocalDate> diasComQueda = new HashSet<>();
        alertas.findByEmpresaIdAndTipoAndCameraIdInAndDataHoraAfter(empresaId, AlertaTipo.FALL_RISK, cameraIds, limiteInstante)
                .stream()
                .filter(a -> a.getDuracaoSegundos() == null || a.getDuracaoSegundos() >= DURACAO_MINIMA_QUEBRA_SEGUNDOS)
                .forEach(a -> diasComQueda.add(a.getDataHora().toLocalDate()));

        int atual = 0;
        for (LocalDate d = hoje; !d.isBefore(limiteDia) && !diasComQueda.contains(d); d = d.minusDays(1)) {
            atual++;
        }

        int melhor = 0;
        int corrente = 0;
        for (LocalDate d = limiteDia; !d.isAfter(hoje); d = d.plusDays(1)) {
            if (diasComQueda.contains(d)) {
                corrente = 0;
            } else {
                corrente++;
                melhor = Math.max(melhor, corrente);
            }
        }
        return new int[]{atual, melhor};
    }
}
