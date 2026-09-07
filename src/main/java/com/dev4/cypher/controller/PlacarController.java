package com.dev4.cypher.controller;

import com.dev4.cypher.dto.PlacarEquipeResponse;
import com.dev4.cypher.service.PlacarService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/placar")
public class PlacarController {

    private final PlacarService service;

    public PlacarController(PlacarService service) {
        this.service = service;
    }

    @GetMapping
    public List<PlacarEquipeResponse> placar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until) {
        OffsetDateTime fim = until != null ? until : OffsetDateTime.now();
        OffsetDateTime inicio = since != null ? since : fim.minusDays(7);
        return service.placar(inicio, fim);
    }
}
