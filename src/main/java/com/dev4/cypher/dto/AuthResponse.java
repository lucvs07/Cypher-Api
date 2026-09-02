package com.dev4.cypher.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Corpo comum de login/me: `papel` sempre presente; `username` vem para
 * GESTOR e para OPERADOR autenticado por credencial (revisão pós-E4), mas
 * fica ausente para OPERADOR autenticado por token de estação — não há
 * conceito de usuário nesse fluxo. `estacaoId` só vem para OPERADOR, nos
 * dois fluxos. `empresaId`/`empresaNome` identificam o tenant autenticado
 * (spec §3). Espelha os DTOs do front (`LoginResponse`/
 * `LoginEstacaoResponse`/`MeResponse`).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(String papel, String username, String estacaoId, String empresaId, String empresaNome) {

    public static AuthResponse gestor(String username, String empresaId, String empresaNome) {
        return new AuthResponse("GESTOR", username, null, empresaId, empresaNome);
    }

    /** OPERADOR por token de estação — sem conceito de usuário. */
    public static AuthResponse operador(String estacaoId, String empresaId, String empresaNome) {
        return new AuthResponse("OPERADOR", null, estacaoId, empresaId, empresaNome);
    }

    /** OPERADOR por usuário/senha (revisão pós-E4) — carrega os dois campos. */
    public static AuthResponse operador(String estacaoId, String username, String empresaId, String empresaNome) {
        return new AuthResponse("OPERADOR", username, estacaoId, empresaId, empresaNome);
    }
}
