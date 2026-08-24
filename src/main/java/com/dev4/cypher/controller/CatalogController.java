package com.dev4.cypher.controller;

import com.dev4.cypher.dto.CameraAdminResponse;
import com.dev4.cypher.dto.CameraConfigResponse;
import com.dev4.cypher.dto.CameraRequest;
import com.dev4.cypher.dto.CameraResponse;
import com.dev4.cypher.dto.EstacaoAdminResponse;
import com.dev4.cypher.dto.StationRequest;
import com.dev4.cypher.dto.StationResponse;
import com.dev4.cypher.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    // ---------------- navegação ----------------

    /** Lista consumida pelo dashboard: somente ativas, sem fonte. */
    @GetMapping("/cameras")
    public List<CameraResponse> cameras(
            @RequestParam(defaultValue = "false") boolean incluirInativas) {
        return service.cameras(incluirInativas);
    }

    @GetMapping("/stations")
    public List<StationResponse> stations(
            @RequestParam(defaultValue = "false") boolean incluirInativas) {
        return service.stations(incluirInativas);
    }

    // ---------------- administração ----------------

    /** Item individual: inclui fonte e responde também para câmera desativada. */
    @GetMapping("/cameras/{id}")
    public ResponseEntity<CameraAdminResponse> camera(@PathVariable String id) {
        return service.camera(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/stations")
    public ResponseEntity<EstacaoAdminResponse> createStation(@Valid @RequestBody StationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createStation(req));
    }

    @PutMapping("/stations/{id}")
    public ResponseEntity<EstacaoAdminResponse> updateStation(@PathVariable String id,
                                                              @Valid @RequestBody StationRequest req) {
        return service.updateStation(id, req)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/stations/{id}")
    public ResponseEntity<Void> deactivateStation(@PathVariable String id) {
        return service.deactivateStation(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** Rotaciona o token de estação; provisionar o kiosk é copiar este valor uma vez (spec E4 §2.4). */
    @PostMapping("/stations/{id}/token/regenerate")
    public ResponseEntity<EstacaoAdminResponse> regenerateStationToken(@PathVariable String id) {
        return service.regenerateToken(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/cameras")
    public ResponseEntity<CameraAdminResponse> createCamera(@Valid @RequestBody CameraRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCamera(req));
    }

    @PutMapping("/cameras/{id}")
    public ResponseEntity<CameraAdminResponse> updateCamera(@PathVariable String id,
                                                            @Valid @RequestBody CameraRequest req) {
        return service.updateCamera(id, req)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/cameras/{id}")
    public ResponseEntity<Void> deactivateCamera(@PathVariable String id) {
        return service.deactivateCamera(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** Superfície de máquina: consumida pelo pipeline no boot. */
    @GetMapping("/cameras/{id}/config")
    public ResponseEntity<CameraConfigResponse> cameraConfig(@PathVariable String id) {
        return service.cameraConfig(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
