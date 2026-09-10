package com.dev4.cypher.dto;

import com.dev4.cypher.model.AlertaNivel;
import com.dev4.cypher.model.AlertaTipo;

import java.time.OffsetDateTime;

/** Uma notificação = um Alerta com estado de leitura, para o usuário GESTOR atual (spec central-notificacoes §2/§3). */
public record NotificacaoResponse(
        Long id,
        String cameraId,
        AlertaTipo type,
        Integer severity,
        AlertaNivel nivel,
        String descricao,
        OffsetDateTime detectedAt,
        boolean lida
) {
}
