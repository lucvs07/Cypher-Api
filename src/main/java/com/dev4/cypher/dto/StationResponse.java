package com.dev4.cypher.dto;

import java.util.List;

/** Estação com as câmeras que a cobrem. É a célula do heatmap. */
public record StationResponse(
        String id,
        String nome,
        String setor,
        boolean ativa,
        List<CameraResponse> cameras
) {
}
