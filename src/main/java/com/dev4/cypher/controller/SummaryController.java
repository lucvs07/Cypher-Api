package com.dev4.cypher.controller;

import com.dev4.cypher.dto.SummaryResponse;
import com.dev4.cypher.service.SummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SummaryController {

    private final SummaryService service;

    public SummaryController(SummaryService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public SummaryResponse summary() {
        return service.build();
    }
}
