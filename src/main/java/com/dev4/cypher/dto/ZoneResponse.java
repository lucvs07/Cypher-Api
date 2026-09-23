package com.dev4.cypher.dto;

import com.dev4.cypher.model.EpiTipo;
import com.dev4.cypher.model.ZoneType;

import java.util.List;

public record ZoneResponse(
        Long id,
        String cameraId,
        String nome,
        ZoneType zoneType,
        List<PontoRequest> polygon,
        int severity,
        List<EpiTipo> requiredPpe,
        boolean delimitaAvaliacao,
        boolean ativa
) {
}
