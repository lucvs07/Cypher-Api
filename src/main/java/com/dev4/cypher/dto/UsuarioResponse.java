package com.dev4.cypher.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** `estacaoId` só vem preenchido para `papel == "OPERADOR"` — omitido para GESTOR. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsuarioResponse(Long id, String username, String papel, String estacaoId) {
}
