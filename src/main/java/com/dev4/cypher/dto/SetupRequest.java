package com.dev4.cypher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Corpo de `POST /api/v1/setup`. `username` e so o "nome" (sem "@empresa") —
 * o backend compoe o username completo como `username + "@" + empresaId`
 * (spec §3.1), para o formato nunca depender do que o cliente manda.
 */
public record SetupRequest(
        @NotBlank
        @Pattern(regexp = "^[^@]+$", message = "username nao pode conter '@'")
        String username,

        @NotBlank String password,

        @NotBlank
        @Pattern(regexp = "^[a-z0-9-]{2,30}$", message = "empresaId deve ser minusculas-numeros-hifen, 2 a 30 caracteres")
        String empresaId,

        @NotBlank String empresaNome
) {
}
