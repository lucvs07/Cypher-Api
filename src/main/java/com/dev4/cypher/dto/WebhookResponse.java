package com.dev4.cypher.dto;

/**
 * {@code secret} e devolvido de proposito — diferente de `Camera.fonte`
 * (nunca exposto), o segredo do webhook e conhecido e configurado pelo
 * proprio GESTOR que o cadastrou (spec §2).
 */
public record WebhookResponse(Long id, String url, String secret, Integer minSeverity, boolean ativo) {
}
