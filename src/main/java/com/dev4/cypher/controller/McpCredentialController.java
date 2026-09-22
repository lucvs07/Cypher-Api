package com.dev4.cypher.controller;

import com.dev4.cypher.dto.McpCredentialCriadaResponse;
import com.dev4.cypher.dto.McpCredentialRequest;
import com.dev4.cypher.dto.McpCredentialResponse;
import com.dev4.cypher.service.McpCredentialService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD de credenciais MCP — GESTOR-only, sem {@code @PreAuthorize}: mesma
 * protecao centralizada em SecurityConfig que ja cobre /webhooks e /teams
 * (cai no catch-all {@code anyRequest().hasRole("GESTOR")}).
 */
@RestController
@RequestMapping("/api/v1/mcp-credentials")
public class McpCredentialController {

    private final McpCredentialService service;

    public McpCredentialController(McpCredentialService service) {
        this.service = service;
    }

    @GetMapping
    public List<McpCredentialResponse> list(@RequestParam(defaultValue = "false") boolean incluirInativas) {
        return service.listar(incluirInativas);
    }

    @PostMapping
    public ResponseEntity<McpCredentialCriadaResponse> create(@Valid @RequestBody McpCredentialRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        return service.revogar(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
