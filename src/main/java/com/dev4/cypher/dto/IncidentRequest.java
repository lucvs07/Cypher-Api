package com.dev4.cypher.dto;

import com.dev4.cypher.model.AlertaTipo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

/** Payload de ingestão vindo do pipeline (spec §3.1). */
public record IncidentRequest(

        @NotBlank String incidentKey,
        @NotBlank String cameraId,
        @NotBlank String runId,
        @NotNull Integer trackId,
        @NotNull AlertaTipo type,
        @NotNull @Min(0) @Max(4) Integer severity,
        @NotBlank String reason,
        @NotBlank String postureCategory,
        @NotNull Integer rebaScore,
        @NotNull Integer actionLevel,
        String zoneName,
        @NotBlank String zoneStatus,
        List<String> epiPresent,
        List<String> epiMissing,
        @NotNull OffsetDateTime detectedAt,
        String snapshotUrl
) {
}
