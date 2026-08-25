package com.dev4.cypher.dto;

/**
 * Resposta dedicada de {@code POST /cameras/{id}/apikey/regenerate} —
 * deliberadamente separada de {@code CameraAdminResponse}, que nunca inclui
 * {@code apiKey} (nem em GET, nem em create/update): a chave só é revelada
 * por esta rota de escrita explícita, mesmo padrão de {@code
 * EstacaoAdminResponse.token} em {@code /stations/{id}/token/regenerate}.
 */
public record CameraApiKeyResponse(String id, String apiKey) {
}
