package com.dev4.cypher.controller;

import com.dev4.cypher.dto.UserRequest;
import com.dev4.cypher.dto.UsuarioResponse;
import com.dev4.cypher.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CRUD de usuário (sempre GESTOR) — GESTOR-only (spec E4 §2.6), travado pela Tarefa 12. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UsuarioService service;

    public UserController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<UsuarioResponse> list() {
        return service.listar();
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> create(@Valid @RequestBody UserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> update(@PathVariable Long id, @Valid @RequestBody UserRequest req) {
        return service.atualizar(id, req)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.remover(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
