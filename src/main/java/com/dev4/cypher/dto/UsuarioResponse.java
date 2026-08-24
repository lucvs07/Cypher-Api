package com.dev4.cypher.dto;

/** `papel` sempre `"GESTOR"` — é o único papel que `Usuario` representa (spec E4 §2.5). */
public record UsuarioResponse(Long id, String username, String papel) {
}
