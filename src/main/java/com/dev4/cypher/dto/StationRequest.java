package com.dev4.cypher.dto;

import com.dev4.cypher.catalog.CatalogCodes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Escrita de estação.
 *
 * <p>{@code ativa} é {@code Boolean} e não {@code boolean}: {@code null}
 * significa "não mexer", que é o que permite um PUT parcial sem reativar por
 * acidente. Reativar é justamente enviar {@code true}.
 */
public record StationRequest(

        @NotBlank
        @Pattern(regexp = CatalogCodes.PADRAO,
                message = "codigo deve ser MAIUSCULAS-ALFANUM, ex.: EST-01")
        String id,

        @NotBlank @Size(max = 120) String nome,
        @Size(max = 120) String setor,
        Boolean ativa
) {
}
