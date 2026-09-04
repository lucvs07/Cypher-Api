package com.dev4.cypher.dto;

import com.dev4.cypher.model.ZoneType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ZoneRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotNull ZoneType zoneType,
        @NotNull @Size(min = 3, message = "poligono precisa de ao menos 3 vertices") List<@Valid @NotNull PontoRequest> polygon,
        @NotNull @Min(1) @Max(4) Integer severity,
        List<String> requiredPpe
) {
}
