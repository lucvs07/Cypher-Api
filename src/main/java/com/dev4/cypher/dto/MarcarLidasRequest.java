package com.dev4.cypher.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MarcarLidasRequest(@NotNull List<Long> alertaIds) {
}
