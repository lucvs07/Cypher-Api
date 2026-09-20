package com.dev4.cypher.controller;

import com.dev4.cypher.dto.EpiChecklistRequest;
import com.dev4.cypher.dto.EpiChecklistResponse;
import com.dev4.cypher.service.EmpresaConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/config")
public class EmpresaConfigController {

    private final EmpresaConfigService service;

    public EmpresaConfigController(EmpresaConfigService service) {
        this.service = service;
    }

    /** Superfície de máquina: consumida pelo pipeline no boot como fallback global de EPI. */
    @GetMapping("/epis")
    public EpiChecklistResponse epiChecklist() {
        return service.epiChecklist();
    }

    @PutMapping("/epis")
    public EpiChecklistResponse atualizarEpiChecklist(@Valid @RequestBody EpiChecklistRequest req) {
        return service.atualizarEpiChecklist(req);
    }
}
