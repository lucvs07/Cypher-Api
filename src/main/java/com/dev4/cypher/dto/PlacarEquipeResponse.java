package com.dev4.cypher.dto;

import com.dev4.cypher.model.Turno;

public record PlacarEquipeResponse(
        String equipeId,
        String nome,
        Turno turno,
        boolean dadosSuficientes,           // false quando cobertura < PISO_COBERTURA (spec §3.2)
        Double conformidade,                 // null quando !dadosSuficientes
        double cobertura,
        int sequenciaAtual,
        int melhorSequencia,
        Double metaConformidade,
        boolean metaConformidadeAtingida,
        Boolean metaZeroSeveridade4Ativa,
        boolean metaZeroSeveridade4Atingida,
        Integer metaTempoReconhecimentoSegundos,
        Double deltaConformidadeSemana       // null quando !dadosSuficientes
) {
}
