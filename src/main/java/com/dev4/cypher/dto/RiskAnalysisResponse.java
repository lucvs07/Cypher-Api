package com.dev4.cypher.dto;

import com.dev4.cypher.model.AnalysisStatus;

import java.time.OffsetDateTime;
import java.util.List;

/** Saída estruturada da análise de risco feita pelo LLM (spec §2.2). */
public record RiskAnalysisResponse(
        String situacao,
        List<String> episIdentificados,
        List<String> episAusentes,
        boolean riscoIdentificado,
        String tipoRisco,
        String nivelRisco,
        String justificativa,
        String acaoRecomendada,
        String provider,
        String promptVersion,
        AnalysisStatus status,
        OffsetDateTime geradoEm
) {
    public static RiskAnalysisResponse erroApi(String provider, String promptVersion) {
        return new RiskAnalysisResponse(null, List.of(), List.of(), false, null, null, null, null,
                provider, promptVersion, AnalysisStatus.ERRO_API, OffsetDateTime.now());
    }

    public static RiskAnalysisResponse erroParse(String provider, String promptVersion) {
        return new RiskAnalysisResponse(null, List.of(), List.of(), false, null, null, null, null,
                provider, promptVersion, AnalysisStatus.ERRO_PARSE, OffsetDateTime.now());
    }
}
