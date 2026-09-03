package com.dev4.cypher.controller;

import com.dev4.cypher.dto.WebhookRequest;
import com.dev4.cypher.dto.WebhookResponse;
import com.dev4.cypher.service.WebhookService;
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

/**
 * CRUD de assinante de webhook (spec §2) — GESTOR-only, sem `@PreAuthorize`
 * aqui: mesma proteção centralizada em `SecurityConfig` que já cobre
 * `/teams`/`/users` (nenhuma linha explicita, cai no catch-all
 * `anyRequest().hasRole("GESTOR")`).
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final WebhookService service;

    public WebhookController(WebhookService service) {
        this.service = service;
    }

    @GetMapping
    public List<WebhookResponse> list() {
        return service.listar();
    }

    @PostMapping
    public ResponseEntity<WebhookResponse> create(@Valid @RequestBody WebhookRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WebhookResponse> update(@PathVariable Long id, @Valid @RequestBody WebhookRequest req) {
        return service.atualizar(id, req)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        return service.desativar(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
