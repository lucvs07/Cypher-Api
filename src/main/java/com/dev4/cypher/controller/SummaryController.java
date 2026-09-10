package com.dev4.cypher.controller;

import com.dev4.cypher.dto.SummaryResponse;
import com.dev4.cypher.service.SummaryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1")
public class SummaryController {

    private final SummaryService service;

    public SummaryController(SummaryService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public SummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until,
            @RequestParam(required = false) String turno,
            @RequestParam(required = false) String setor,
            @RequestParam(required = false) String epi,
            @RequestParam(required = false, defaultValue = "false") boolean detalhado) {
        return service.build(since, until, turno, setor, epi, detalhado);
    }
}
