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
        List<DayCount> ultimosSeteDias
) {

    /** Célula do heatmap: uma estação e sua contagem de hoje. */
    public record StationCell(String estacaoId, String nome, String setor, int incidentes) {
    }

    public record DayCount(LocalDate dia, int incidentes) {
    }
}
