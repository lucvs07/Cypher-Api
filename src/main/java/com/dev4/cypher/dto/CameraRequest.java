package com.dev4.cypher.dto;

import com.dev4.cypher.catalog.CatalogCodes;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Escrita de câmera.
 *
 * <p>{@code fonte} guarda placeholders, nunca segredo: o pipeline expande
 * {@code ${VAR}} do próprio ambiente (spec §3).
 */
public record CameraRequest(

        @NotBlank
        @Pattern(regexp = CatalogCodes.PADRAO,
                message = "codigo deve ser MAIUSCULAS-ALFANUM, ex.: CAM-01")
        String id,

        @NotBlank @Size(max = 120) String nome,
        @NotBlank String estacaoId,
        @NotBlank @Size(max = 500) String streamUrl,
        @Size(max = 500) String fonte,
        Boolean ativa,
        Integer rotacao,
        @Min(-45) @Max(45) Integer anguloCameraGraus,
        Double fallenAspectRatio,
        Double fallenVerticalExtent,
        Double trunkRotationMax,
        Boolean fallRequireCorroboration
) {
}
