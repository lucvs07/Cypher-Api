package com.dev4.cypher.dto;

import java.time.OffsetDateTime;

public record McpCredentialResponse(
        Long id,
        String nome,
        OffsetDateTime criadaEm,
        OffsetDateTime ultimoUsoEm,
        boolean ativa
) {
}
