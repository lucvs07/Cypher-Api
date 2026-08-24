package com.dev4.cypher.dto;

import com.dev4.cypher.catalog.CatalogCodes;
import com.dev4.cypher.model.Turno;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TeamRequest(

        @NotBlank
        @Pattern(regexp = CatalogCodes.PADRAO, message = "codigo deve ser MAIUSCULAS-ALFANUM, ex.: EQ-01")
        String id,

        @NotBlank @Size(max = 120) String nome,
        @NotNull Turno turno,
        @NotEmpty List<@NotBlank String> estacaoIds,
        Boolean ativa
) {
}
