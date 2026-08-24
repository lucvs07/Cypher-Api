package com.dev4.cypher.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Corpo comum de login/me: `papel` sempre presente, `username` OU
 * `estacaoId` conforme o papel — nunca os dois. Espelha os dois DTOs do
 * front (`LoginResponse`/`LoginEstacaoResponse`), unificados aqui porque o
 * backend decide qual campo preencher a partir do tipo de `Authentication`.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(String papel, String username, String estacaoId) {

    public static AuthResponse gestor(String username) {
        return new AuthResponse("GESTOR", username, null);
    }

    public static AuthResponse operador(String estacaoId) {
        return new AuthResponse("OPERADOR", null, estacaoId);
    }
}
