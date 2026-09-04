package com.dev4.cypher.service;

import com.dev4.cypher.dto.PontoRequest;
import com.dev4.cypher.dto.ZoneRequest;
import com.dev4.cypher.dto.ZoneResponse;
import com.dev4.cypher.model.Camera;
import com.dev4.cypher.model.PontoNormalizado;
import com.dev4.cypher.model.Zona;
import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.repository.ZonaRepository;
import com.dev4.cypher.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** CRUD de zona de risco (spec §2), aninhado numa câmera e escopado por empresa. */
@Service
public class ZonaService {

    private final ZonaRepository zonas;
    private final CameraRepository cameras;

    public ZonaService(ZonaRepository zonas, CameraRepository cameras) {
        this.zonas = zonas;
        this.cameras = cameras;
    }

    @Transactional(readOnly = true)
    public Optional<List<ZoneResponse>> listar(String codigo) {
        return camera(codigo).map(camera ->
                zonas.findByCameraIdAndEmpresaIdAndAtivaTrue(camera.getId(), TenantContext.currentEmpresaId())
                        .stream().map(this::toResponse).toList());
    }

    @Transactional
    public Optional<ZoneResponse> criar(String codigo, ZoneRequest req) {
        return camera(codigo).map(camera -> {
            Zona nova = new Zona(null, camera.getId(), req.nome(), req.zoneType(),
                    toPontos(req.polygon()), req.severity(),
                    new java.util.ArrayList<>(req.requiredPpe() == null ? List.of() : req.requiredPpe()),
                    true, TenantContext.currentEmpresaId());
            return toResponse(zonas.saveAndFlush(nova));
        });
    }

    @Transactional
    public Optional<ZoneResponse> atualizar(String codigo, Long id, ZoneRequest req) {
        return doTenant(codigo, id).map(zona -> {
            zona.setNome(req.nome());
            zona.setZoneType(req.zoneType());
            zona.setPolygon(toPontos(req.polygon()));
            zona.setSeverity(req.severity());
            zona.setRequiredPpe(new java.util.ArrayList<>(req.requiredPpe() == null ? List.of() : req.requiredPpe()));
            return toResponse(zonas.saveAndFlush(zona));
        });
    }

    @Transactional
    public Optional<Boolean> desativar(String codigo, Long id) {
        return doTenant(codigo, id).map(zona -> {
            zona.setAtiva(false);
            zonas.saveAndFlush(zona);
            return true;
        });
    }

    /** Devolve a câmera só se pertencer ao tenant atual — resolve o código puro da URL para a chave prefixada. */
    Optional<Camera> camera(String codigo) {
        return cameras.findById(TenantContext.key(codigo));
    }

    /** Devolve a zona só se pertencer à câmera E ao tenant informados na URL. */
    Optional<Zona> doTenant(String codigo, Long id) {
        return camera(codigo).flatMap(camera ->
                zonas.findById(id).filter(z -> z.getCameraId().equals(camera.getId())));
    }

    private List<PontoNormalizado> toPontos(List<PontoRequest> polygon) {
        return new java.util.ArrayList<>(polygon.stream().map(p -> new PontoNormalizado(p.x(), p.y())).toList());
    }

    ZoneResponse toResponse(Zona z) {
        List<PontoRequest> polygon = z.getPolygon().stream()
                .map(p -> new PontoRequest(p.getX(), p.getY())).toList();
        return new ZoneResponse(z.getId(), TenantContext.codigo(z.getCameraId()), z.getNome(), z.getZoneType(),
                polygon, z.getSeverity(), z.getRequiredPpe(), z.isAtiva());
    }
}
