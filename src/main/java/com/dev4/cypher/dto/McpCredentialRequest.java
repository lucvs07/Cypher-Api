package com.dev4.cypher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record McpCredentialRequest(
        @NotBlank @Size(max = 120) String nome
) {
}
