package com.dev4.cypher.event;

import com.dev4.cypher.dto.IncidentResponse;

/** Publicado quando um incidente novo é persistido. */
public record IncidentCreatedEvent(IncidentResponse incident) {
}
