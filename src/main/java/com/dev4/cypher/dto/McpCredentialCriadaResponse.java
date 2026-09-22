package com.dev4.cypher.dto;

import java.time.OffsetDateTime;

/** Resposta do POST de criacao — unico momento em que {@code apiKey} (texto puro) aparece. */
public record McpCredentialCriadaResponse(
        Long id,
        String nome,
        OffsetDateTime criadaEm,
        boolean ativa,
        String apiKey
) {
}
