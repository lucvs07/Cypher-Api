package com.dev4.cypher.dto;

/**
 * Estação na superfície de administração — inclui {@code token}, que a lista
 * de navegação (`StationResponse`) omite. Só devolvido por endpoints
 * GESTOR-only: cadastro (create/update) e `/token/regenerate` (spec E4 §2.4).
 */
public record EstacaoAdminResponse(
        String id,
        String nome,
        String setor,
        boolean ativa,
        String token
) {
}
