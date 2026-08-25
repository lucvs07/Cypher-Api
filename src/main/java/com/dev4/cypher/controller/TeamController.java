package com.dev4.cypher.controller;

import com.dev4.cypher.dto.TeamRequest;
import com.dev4.cypher.dto.TeamResponse;
import com.dev4.cypher.service.EquipeService;
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

/** CRUD de equipe — GESTOR-only (spec E4 §2.6/§3), travado pela Tarefa 12. */
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final EquipeService service;

    public TeamController(EquipeService service) {
        this.service = service;
    }

    @GetMapping
    public List<TeamResponse> list() {
        return service.listar();
    }

    @PostMapping
    public ResponseEntity<TeamResponse> create(@Valid @RequestBody TeamRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeamResponse> update(@PathVariable String id, @Valid @RequestBody TeamRequest req) {
        return service.atualizar(id, req)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return service.remover(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
