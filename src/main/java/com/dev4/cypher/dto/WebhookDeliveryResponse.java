package com.dev4.cypher.dto;

import com.dev4.cypher.model.DeliveryStatus;

import java.time.OffsetDateTime;

public record WebhookDeliveryResponse(
        Long id,
        String incidentKey,
        DeliveryStatus status,
        int tentativas,
        String ultimoErro,
        OffsetDateTime proximaTentativa
) {
}
