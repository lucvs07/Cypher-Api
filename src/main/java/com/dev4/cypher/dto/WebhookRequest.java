package com.dev4.cypher.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Escrita de assinante de webhook (spec §2). {@code secret} e digitado pelo
 * gestor, nunca gerado pelo sistema — diferente de `Estacao.token`/`Camera.apiKey`
 * (spec §2.1): frequentemente e um valor combinado com o sistema externo.
 */
public record WebhookRequest(
        @NotBlank @Size(max = 500) String url,
        @NotBlank @Size(max = 200) String secret,
        @NotNull @Min(0) @Max(4) Integer minSeverity,
        Boolean ativo
) {
}
