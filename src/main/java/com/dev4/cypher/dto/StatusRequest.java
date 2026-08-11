package com.dev4.cypher.dto;

import com.dev4.cypher.model.AlertaStatus;
import jakarta.validation.constraints.NotNull;

/** Reconhecimento do incidente pelo operador. */
public record StatusRequest(@NotNull AlertaStatus status) {
}
