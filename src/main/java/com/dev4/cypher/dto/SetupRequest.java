package com.dev4.cypher.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo de `POST /api/v1/setup` — mesmo formato de `LoginRequest` (spec E4 §2.5). */
public record SetupRequest(@NotBlank String username, @NotBlank String password) {
}
