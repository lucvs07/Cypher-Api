package com.dev4.cypher.event;

import com.dev4.cypher.dto.IncidentResponse;

/** Publicado em fechamento, chegada de snapshot ou mudança de status. */
public record IncidentChangedEvent(IncidentResponse incident) {
}
