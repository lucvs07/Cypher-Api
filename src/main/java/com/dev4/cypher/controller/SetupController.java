package com.dev4.cypher.controller;

import com.dev4.cypher.dto.SetupStatusResponse;
import com.dev4.cypher.repository.UsuarioRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Setup de execução única do gestor mestre (spec E4 §2.5) — substitui o seed
 * estático de `application.yaml` descartado na revisão de design. Nesta
 * tarefa só existe `GET /setup/status`, que não depende de nada além do
 * repositório; `POST /setup` (que precisa autenticar a sessão de resposta)
 * só é acrescentado na Tarefa 7, quando `GestorAuthentication` e o bean
 * `SecurityContextRepository` já existem.
 */
@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {

    private final UsuarioRepository usuarios;

    public SetupController(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping("/status")
    public SetupStatusResponse status() {
        return new SetupStatusResponse(usuarios.count() > 0);
    }
}
