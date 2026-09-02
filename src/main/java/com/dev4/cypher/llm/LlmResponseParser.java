package com.dev4.cypher.llm;

import com.dev4.cypher.dto.RiskAnalysisResponse;
import com.dev4.cypher.model.AnalysisStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Interpreta o texto bruto devolvido pelo LLM. Nunca lanca excecao — uma
 * resposta que nao e o JSON esperado vira ERRO_PARSE, nao um erro 5xx
 * (spec §2.5).
 */
@Component
public class LlmResponseParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public RiskAnalysisResponse parse(String bruto, String provider, String promptVersion) {
        try {
            JsonNode raiz = mapper.readTree(extrairJson(bruto));
            return new RiskAnalysisResponse(
                    raiz.path("situacao").asText(""),
                    listaDe(raiz, "epis_identificados"),
                    listaDe(raiz, "epis_ausentes"),
                    raiz.path("risco_identificado").asBoolean(false),
                    raiz.path("tipo_risco").asText(null),
                    raiz.path("nivel_risco").asText(""),
                    raiz.path("justificativa").asText(""),
                    raiz.path("acao_recomendada").asText(""),
                    provider,
                    promptVersion,
                    AnalysisStatus.OK,
                    OffsetDateTime.now());
        } catch (Exception falha) {
            return RiskAnalysisResponse.erroParse(provider, promptVersion);
        }
    }

    private List<String> listaDe(JsonNode raiz, String campo) {
        List<String> valores = new ArrayList<>();
        raiz.path(campo).forEach(item -> valores.add(item.asText("")));
        return valores;
    }

    /** O modelo as vezes envolve o JSON em texto ou cerca de markdown; extrai so o objeto. */
    private String extrairJson(String bruto) {
        int inicio = bruto.indexOf('{');
        int fim = bruto.lastIndexOf('}');
        if (inicio < 0 || fim < 0 || fim < inicio) {
            throw new IllegalArgumentException("Nenhum objeto JSON encontrado na resposta");
        }
        return bruto.substring(inicio, fim + 1);
    }
}
