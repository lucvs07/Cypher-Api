package com.dev4.cypher.controller;

import com.dev4.cypher.dto.ZoneRequest;
import com.dev4.cypher.dto.ZoneResponse;
import com.dev4.cypher.service.ZonaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD de zona de risco (spec §2) — GESTOR-only, sem `@PreAuthorize`: mesma
 * proteção centralizada em `SecurityConfig` que já cobre `/teams`/`/users`/`/webhooks`
 * (nenhuma linha explicita para o CRUD, cai no catch-all `anyRequest().hasRole("GESTOR")`).
 * O endpoint de máquina `.../zones/config` é a exceção, tratada na Tarefa 2.
 */
@RestController
@RequestMapping("/api/v1/cameras/{codigo}/zones")
public class ZonaController {

    private final ZonaService service;

    public ZonaController(ZonaService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ZoneResponse>> list(@PathVariable String codigo) {
        return service.listar(codigo).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/config")
    public ResponseEntity<List<ZoneResponse>> config(@PathVariable String codigo) {
        return service.config(codigo).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ZoneResponse> create(@PathVariable String codigo, @Valid @RequestBody ZoneRequest req) {
        return service.criar(codigo, req)
                .map(z -> ResponseEntity.status(HttpStatus.CREATED).body(z))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ZoneResponse> update(@PathVariable String codigo, @PathVariable Long id,
                                               @Valid @RequestBody ZoneRequest req) {
        return service.atualizar(codigo, id, req)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable String codigo, @PathVariable Long id) {
        return service.desativar(codigo, id)
                .map(ok -> ResponseEntity.noContent().<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
