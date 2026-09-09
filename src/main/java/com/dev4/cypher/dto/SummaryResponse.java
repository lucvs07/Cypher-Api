package com.dev4.cypher.dto;

import java.time.LocalDate;
import java.util.List;

/** Agregados que alimentam o dashboard sem que o browser precise contar nada. */
public record SummaryResponse(
        int incidentesHoje,
        int incidentesOntem,
        int criticosHoje,
        int pendentes,
        List<StationCell> heatmap,
        List<DayCount> ultimosSeteDias,
        List<TemporalCell> distribuicaoTemporal,
        List<DayCount> historicoAnual
) {

    /** Célula do heatmap: uma estação e sua contagem na janela filtrada. */
    public record StationCell(String estacaoId, String nome, String setor, int incidentes) {
    }

    public record DayCount(LocalDate dia, int incidentes) {
    }

    /** Célula do painel Temporal: dia da semana (0=Seg..6=Dom) x faixa de 2h (0..11). */
    public record TemporalCell(int diaSemana, int faixa2h, int incidentes) {
    }
}
