package com.dev4.cypher.dto;

import com.dev4.cypher.model.ZoneType;

import java.util.List;

public record ZoneResponse(
        Long id,
        String cameraId,
        String nome,
        ZoneType zoneType,
        List<PontoRequest> polygon,
        int severity,
        List<String> requiredPpe,
        boolean delimitaAvaliacao,
        boolean ativa
) {
}
