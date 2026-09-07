package com.dev4.cypher.controller;

import com.dev4.cypher.dto.IncidentResponse;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.report.CsvReportBuilder;
import com.dev4.cypher.report.PdfReportBuilder;
import com.dev4.cypher.service.IncidentService;
import com.dev4.cypher.service.PlacarService;
import com.dev4.cypher.service.SummaryService;
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
    private final PlacarService placarService;
    private final SummaryService summaryService;

    public ReportController(IncidentService incidentService, PlacarService placarService,
                            SummaryService summaryService) {
        this.incidentService = incidentService;
        this.placarService = placarService;
        this.summaryService = summaryService;
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

    @GetMapping("/placar.csv")
    public ResponseEntity<byte[]> placarCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until) throws IOException {
        OffsetDateTime fim = until != null ? until : OffsetDateTime.now();
        OffsetDateTime inicio = since != null ? since : fim.minusDays(7);
        String csv = CsvReportBuilder.placarParaCsv(placarService.placar(inicio, fim));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=placar.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/summary.pdf")
    public ResponseEntity<byte[]> summaryPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until) throws IOException {
        OffsetDateTime fim = until != null ? until : OffsetDateTime.now();
        OffsetDateTime inicio = since != null ? since : fim.minusDays(7);
        byte[] pdf = PdfReportBuilder.resumoExecutivo(summaryService.build(), placarService.placar(inicio, fim));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resumo.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
