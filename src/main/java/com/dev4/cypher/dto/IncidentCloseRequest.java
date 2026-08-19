package com.dev4.cypher.dto;

import com.dev4.cypher.model.ClosedReason;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.OffsetDateTime;

/**
 * Fechamento do episódio e/ou chegada tardia do snapshot (spec §3.3).
 * Todos os campos são opcionais e aplicados individualmente.
 */
public record IncidentCloseRequest(
        OffsetDateTime endedAt,
        Double durationSeconds,
        @Min(0) @Max(4) Integer peakSeverity,
        ClosedReason closedReason,
        String snapshotUrl
) {
}
