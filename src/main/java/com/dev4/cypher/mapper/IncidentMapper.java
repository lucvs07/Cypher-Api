package com.dev4.cypher.mapper;

import com.dev4.cypher.dto.IncidentRequest;
import com.dev4.cypher.dto.IncidentResponse;
import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.model.AlertaNivel;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.model.AlertaTipo;
import com.dev4.cypher.security.TenantContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Única fronteira entre o contrato de fio (inglês) e a persistência
 * (português). Nada além desta classe deve traduzir nomes de campo.
 */
@Component
public class IncidentMapper {

    public Alerta toEntity(IncidentRequest req) {
        Alerta a = new Alerta();
        a.setIncidentKey(req.incidentKey());
        a.setCameraId(req.cameraId());
        a.setRunId(req.runId());
        a.setTrackId(req.trackId());
        a.setTipo(req.type());
        a.setSeveridade(req.severity());
        a.setNivel(AlertaNivel.fromSeverity(req.severity()));
        a.setReason(req.reason());
        a.setPosturaCategoria(req.postureCategory());
        a.setRebaScore(req.rebaScore());
        a.setActionLevel(req.actionLevel());
        a.setZona(req.zoneName());
        a.setZoneStatus(req.zoneStatus());
        a.setEpiPresent(req.epiPresent() == null ? List.of() : List.copyOf(req.epiPresent()));
        a.setEpiMissing(req.epiMissing() == null ? List.of() : List.copyOf(req.epiMissing()));
        a.setSnapshotUrl(req.snapshotUrl());
        a.setDataHora(req.detectedAt());
        a.setDescricao(descricaoDe(req.type(), req.reason(), req.cameraId()));
        // Status é sempre atribuído pelo servidor; nunca vem do cliente.
        a.setStatus(AlertaStatus.PENDENTE);
        a.setEmpresaId(TenantContext.currentEmpresaId());
        return a;
    }

    public IncidentResponse toResponse(Alerta a) {
        return new IncidentResponse(
                a.getId(),
                a.getIncidentKey(),
                a.getCameraId(),
                a.getRunId(),
                a.getTrackId(),
                a.getTipo(),
                a.getSeveridade(),
                a.getNivel(),
                a.getDescricao(),
                a.getReason(),
                a.getPosturaCategoria(),
                a.getRebaScore(),
                a.getActionLevel(),
                a.getZona(),
                a.getZoneStatus(),
                a.getEpiPresent(),
                a.getEpiMissing(),
                a.getSnapshotUrl(),
                a.getDataHora(),
                a.getEncerradoEm(),
                a.getDuracaoSegundos(),
                a.getSeveridadePico(),
                a.getClosedReason(),
                a.getStatus());
    }

    /** Texto legível exibido no painel; gerado no servidor, nunca enviado pelo cliente. */
    public String descricaoDe(AlertaTipo tipo, String reason, String cameraId) {
        String assunto = switch (tipo) {
            case FALL_RISK -> "Queda ou risco imediato detectado";
            case ERGONOMIC_RISK -> "Postura ergonomicamente inadequada";
            case ZONE_VIOLATION -> "Pessoa em zona critica";
            case EPI_VIOLATION -> "EPI obrigatorio ausente";
        };
        return "%s (%s) na camera %s".formatted(assunto, reason, cameraId);
    }
}
