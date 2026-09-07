package com.dev4.cypher.controller;

import com.dev4.cypher.dto.IncidentResponse;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.report.CsvReportBuilder;
import com.dev4.cypher.service.IncidentService;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

/**
 * Projeções em arquivo de dados já existentes (spec E6 §1) — GESTOR-only,
 * sem escopo de estação de OPERADOR: relatório é ação de gestão.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final IncidentService incidentService;

    public ReportController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping("/incidents.csv")
    public ResponseEntity<byte[]> incidentsCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(required = false) String camera,
            @RequestParam(required = false) Integer severityMin,
            @RequestParam(required = false) AlertaStatus status) throws IOException {
        java.util.List<IncidentResponse> incidentes = incidentService
                .search(since, camera, severityMin, status, null, Pageable.unpaged())
                .getContent();
        String csv = CsvReportBuilder.incidentesParaCsv(incidentes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=incidentes.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
