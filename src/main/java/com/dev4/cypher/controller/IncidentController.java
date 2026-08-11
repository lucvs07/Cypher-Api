package com.dev4.cypher.controller;

import com.dev4.cypher.dto.IncidentCloseRequest;
import com.dev4.cypher.dto.IncidentRequest;
import com.dev4.cypher.dto.IncidentResponse;
import com.dev4.cypher.dto.StatusRequest;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

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

    /**
     * Histórico com filtros opcionais, mais recentes primeiro.
     *
     * <p>{@code since} é o que o front usa para cobrir a lacuna depois de o SSE
     * cair e reconectar.
     */
    @GetMapping
    public Page<IncidentResponse> list(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(required = false) String camera,
            @RequestParam(required = false) Integer severityMin,
            @RequestParam(required = false) AlertaStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "dataHora"));
        return service.search(since, camera, severityMin, status, pageable);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<IncidentResponse> changeStatus(@PathVariable Long id,
                                                         @Valid @RequestBody StatusRequest req) {
        return service.changeStatus(id, req.status())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
