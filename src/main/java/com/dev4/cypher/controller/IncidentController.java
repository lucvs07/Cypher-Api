package com.dev4.cypher.controller;

import com.dev4.cypher.dto.IncidentCloseRequest;
import com.dev4.cypher.dto.IncidentRequest;
import com.dev4.cypher.dto.IncidentResponse;
import com.dev4.cypher.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService service;

    public IncidentController(IncidentService service) {
        this.service = service;
    }

    /**
     * Ingestão vinda do pipeline. Idempotente por {@code incidentKey}: um retry
     * após timeout devolve 200 com o incidente existente, em vez de duplicar.
     */
    @PostMapping
    public ResponseEntity<IncidentResponse> ingest(@Valid @RequestBody IncidentRequest req) {
        IncidentService.IngestResult resultado = service.ingest(req);
        HttpStatus status = resultado.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(resultado.incident());
    }

    /** Fechamento do episódio e/ou chegada tardia do snapshot. */
    @PatchMapping("/{incidentKey}")
    public ResponseEntity<IncidentResponse> close(@PathVariable String incidentKey,
                                                  @Valid @RequestBody IncidentCloseRequest req) {
        return service.close(incidentKey, req)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
