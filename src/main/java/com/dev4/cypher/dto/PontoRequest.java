package com.dev4.cypher.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record PontoRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double x,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double y
) {
}
