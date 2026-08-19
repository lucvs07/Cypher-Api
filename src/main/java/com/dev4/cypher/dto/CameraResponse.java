package com.dev4.cypher.dto;

/**
 * Câmera como o front a enxerga.
 *
 * <p>{@code fonte} é omitida de propósito: pode conter credencial embutida na
 * URL RTSP, e o browser consome apenas {@code streamUrl}.
 */
public record CameraResponse(
        String id,
        String nome,
        String estacaoId,
        String streamUrl
) {
}
