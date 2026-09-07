package com.dev4.cypher.controller;

import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.security.TenantContext;
import com.dev4.cypher.service.MonitoramentoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * Superfície de máquina (spec §2.1) — mesmo padrão de ingestão de
 * incidente: só `PIPELINE` (X-Cypher-Key), nunca sessão.
 */
@RestController
@RequestMapping("/api/v1/cameras/{codigo}/heartbeat")
public class HeartbeatController {

    private final CameraRepository cameras;
    private final MonitoramentoService monitoramento;

    public HeartbeatController(CameraRepository cameras, MonitoramentoService monitoramento) {
        this.cameras = cameras;
        this.monitoramento = monitoramento;
    }

    @PostMapping
    public ResponseEntity<Void> heartbeat(@PathVariable String codigo) {
        return cameras.findById(TenantContext.key(codigo)).map(camera -> {
            monitoramento.registrar(camera.getId(), camera.getEmpresaId(), OffsetDateTime.now());
            return ResponseEntity.noContent().<Void>build();
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
