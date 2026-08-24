package com.dev4.cypher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * `password` é `String` simples (não `@NotBlank`): obrigatória no cadastro,
 * ausente ou vazia numa edição que não redefine senha — validado em
 * `UsuarioService`, não por anotação, porque o mesmo DTO serve às duas
 * operações (mesmo padrão de `StationRequest.ativa`).
 */
public record UserRequest(
        @NotBlank @Size(max = 60) String username,
        @NotBlank String papel,
        String password
) {
}
