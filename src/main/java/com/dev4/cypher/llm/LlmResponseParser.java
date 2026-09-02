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
        return parse(bruto, provider, promptVersion, OffsetDateTime.now());
    }

    /**
     * Mesma interpretacao, com timestamp explicito — usado para reconstruir
     * uma analise ja salva (spec de "ultima analise"), onde `geradoEm` deve
     * refletir quando a chamada realmente aconteceu, nao o instante da
     * releitura.
     */
    public RiskAnalysisResponse parse(String bruto, String provider, String promptVersion,
                                       OffsetDateTime geradoEm) {
        try {
            JsonNode raiz = mapper.readTree(extrairJson(bruto));
            if (!temFormatoMinimoEsperado(raiz)) {
                throw new IllegalArgumentException(
                        "JSON nao possui o formato minimo esperado (situacao/nivel_risco)");
            }
            return new RiskAnalysisResponse(
                    raiz.path("situacao").asString(""),
                    listaDe(raiz, "epis_identificados"),
                    listaDe(raiz, "epis_ausentes"),
                    raiz.path("risco_identificado").asBoolean(false),
                    raiz.path("tipo_risco").asString(null),
                    raiz.path("nivel_risco").asString(""),
                    raiz.path("justificativa").asString(""),
                    raiz.path("acao_recomendada").asString(""),
                    provider,
                    promptVersion,
                    AnalysisStatus.OK,
                    geradoEm);
        } catch (Exception falha) {
            return RiskAnalysisResponse.erroParse(provider, promptVersion, geradoEm);
        }
    }

    /** Marcadores minimos do schema esperado — sem eles, nao e uma resposta valida (spec §2.5). */
    private boolean temFormatoMinimoEsperado(JsonNode raiz) {
        return raiz.isObject() && raiz.has("situacao") && raiz.has("nivel_risco");
    }

    private List<String> listaDe(JsonNode raiz, String campo) {
        List<String> valores = new ArrayList<>();
        raiz.path(campo).forEach(item -> valores.add(item.asString("")));
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
