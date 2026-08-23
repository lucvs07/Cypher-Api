package com.dev4.cypher.dto;

/**
 * Config que o pipeline lê no boot.
 *
 * <p>{@code fonte} vem com placeholders {@code ${VAR}}: o segredo vive no
 * ambiente do host do pipeline e nunca atravessa a rede (spec §3).
 *
 * <p>{@code streamPort} é derivado do {@code streamUrl} cadastrado, o que
 * elimina a duplicação em que a porta era declarada em dois arquivos
 * independentes e podia divergir.
 */
public record CameraConfigResponse(
        String codigo,
        String nome,
        String estacaoId,
        String fonte,
        int streamPort,
        boolean ativa
) {
}
