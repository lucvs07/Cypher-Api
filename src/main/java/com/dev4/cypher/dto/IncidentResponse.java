package com.dev4.cypher.dto;

import com.dev4.cypher.model.AlertaNivel;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.model.AlertaTipo;
import com.dev4.cypher.model.ClosedReason;

import java.time.OffsetDateTime;
import java.util.List;

/** Representação de saída de um incidente. */
public record IncidentResponse(
        Long id,
        String incidentKey,
        String cameraId,
        String runId,
        Integer trackId,
        AlertaTipo type,
        Integer severity,
        AlertaNivel nivel,
        String descricao,
        String reason,
        String postureCategory,
        Integer rebaScore,
        Integer actionLevel,
        String zoneName,
        String zoneStatus,
        List<String> epiPresent,
        List<String> epiMissing,
        String snapshotUrl,
        OffsetDateTime detectedAt,
        OffsetDateTime endedAt,
        Double durationSeconds,
        Integer peakSeverity,
        ClosedReason closedReason,
        AlertaStatus status
) {
}
