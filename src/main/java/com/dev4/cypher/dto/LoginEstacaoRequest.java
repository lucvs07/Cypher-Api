package com.dev4.cypher.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginEstacaoRequest(@NotBlank String token) {
}
