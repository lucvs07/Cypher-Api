package com.dev4.cypher.llm;

import com.dev4.cypher.model.Alerta;
import org.springframework.stereotype.Component;

/** Monta o prompt enviado ao LLM multimodal (spec §2.6). */
@Component
public class PromptBuilder {

    private static final String INSTRUCOES_FORMATO = """
            Responda apenas com um objeto JSON, sem nenhum texto antes ou depois, exatamente neste formato:
            {
              "situacao": "string descrevendo a cena",
              "epis_identificados": ["lista", "de", "epis"],
              "epis_ausentes": ["lista", "de", "epis"],
              "risco_identificado": true ou false,
              "tipo_risco": "string ou null",
              "nivel_risco": "baixo, medio ou alto",
              "justificativa": "string",
              "acao_recomendada": "string"
            }
            """;

    public String montar(Alerta alerta, String versao) {
        if ("v1".equals(versao)) {
            return montarV1();
        }
        return montarV2(alerta);
    }

    private String montarV1() {
        return """
                Voce e um assistente de seguranca industrial. Analise a imagem anexada e identifique \
                riscos relacionados a uso de EPIs, proximidade de zonas perigosas ou posturas inadequadas.

                """ + INSTRUCOES_FORMATO;
    }

    private String montarV2(Alerta alerta) {
        String zona = alerta.getZona() != null ? alerta.getZona() : "nenhuma";
        String episPresentes = alerta.getEpiPresent().isEmpty()
                ? "nenhum" : String.join(", ", alerta.getEpiPresent());
        String episAusentes = alerta.getEpiMissing().isEmpty()
                ? "nenhum" : String.join(", ", alerta.getEpiMissing());

        return ("""
                Voce e um assistente de seguranca industrial. Um sistema de visao computacional ja \
                analisou esta cena e identificou os seguintes sinais preliminares:
                - categoria de postura: %s
                - zona: %s
                - EPIs presentes: %s
                - EPIs ausentes: %s
                - score REBA: %s
                - severidade calculada: %s

                Analise a imagem anexada e CONFIRME, REFINE ou CONTESTE esses sinais com base no que \
                voce observa. Nao assuma que os sinais acima estao corretos.

                """).formatted(alerta.getPosturaCategoria(), zona, episPresentes, episAusentes,
                        alerta.getRebaScore(), alerta.getSeveridade())
                + INSTRUCOES_FORMATO;
    }
}
