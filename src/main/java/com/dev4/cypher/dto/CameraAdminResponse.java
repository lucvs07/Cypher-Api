package com.dev4.cypher.dto;

/**
 * Câmera na superfície de administração — inclui {@code fonte}, que a lista
 * de navegação omite.
 *
 * <p>A fonte carrega placeholders, não segredo, mas revela topologia interna
 * (host, nomes de variável) que o dashboard não tem uso para. Separar as duas
 * representações é a fronteira que E4 vai proteger.
 */
public record CameraAdminResponse(
        String id,
        String nome,
        String estacaoId,
        String streamUrl,
        String fonte,
        boolean ativa,
        int rotacao,
        int anguloCameraGraus,
        Double fallenAspectRatio,
        Double fallenVerticalExtent,
        Double trunkRotationMax,
        Boolean fallRequireCorroboration
) {
}
