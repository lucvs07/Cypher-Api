package com.dev4.cypher.llm;

import com.dev4.cypher.model.AnalysisStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmResponseParserTest {

    private final LlmResponseParser parser = new LlmResponseParser();

    @Test
    void jsonValidoVideStatusOk() {
        String bruto = """
                {
                  "situacao": "trabalhador proximo a prensa",
                  "epis_identificados": ["capacete"],
                  "epis_ausentes": ["luvas"],
                  "risco_identificado": true,
                  "tipo_risco": "aproximacao de zona perigosa",
                  "nivel_risco": "alto",
                  "justificativa": "muito perto da area de movimentacao",
                  "acao_recomendada": "interromper a operacao"
                }
                """;

        var resultado = parser.parse(bruto, "claude", "v2");

        assertThat(resultado.status()).isEqualTo(AnalysisStatus.OK);
        assertThat(resultado.situacao()).isEqualTo("trabalhador proximo a prensa");
        assertThat(resultado.episIdentificados()).containsExactly("capacete");
        assertThat(resultado.episAusentes()).containsExactly("luvas");
        assertThat(resultado.riscoIdentificado()).isTrue();
        assertThat(resultado.nivelRisco()).isEqualTo("alto");
        assertThat(resultado.provider()).isEqualTo("claude");
        assertThat(resultado.promptVersion()).isEqualTo("v2");
    }

    @Test
    void jsonEnvolvidoEmCercaDeMarkdownAindaEExtraido() {
        String bruto = "Aqui esta a analise:\n```json\n{\"situacao\": \"ok\", \"epis_identificados\": [], "
                + "\"epis_ausentes\": [], \"risco_identificado\": false, \"tipo_risco\": null, "
                + "\"nivel_risco\": \"baixo\", \"justificativa\": \"nada de anormal\", "
                + "\"acao_recomendada\": \"nenhuma\"}\n```";

        var resultado = parser.parse(bruto, "claude", "v1");

        assertThat(resultado.status()).isEqualTo(AnalysisStatus.OK);
        assertThat(resultado.situacao()).isEqualTo("ok");
    }

    @Test
    void textoSemNenhumJsonVideErroParse() {
        var resultado = parser.parse("desculpe, nao consigo analisar esta imagem.", "gemini", "v1");

        assertThat(resultado.status()).isEqualTo(AnalysisStatus.ERRO_PARSE);
        assertThat(resultado.provider()).isEqualTo("gemini");
        assertThat(resultado.promptVersion()).isEqualTo("v1");
    }

    @Test
    void jsonValidoMasForaDoEsquemaEsperadoVideErroParse() {
        var resultado = parser.parse("{\"error\": \"nao posso analisar esta imagem\"}", "gemini", "v1");

        assertThat(resultado.status()).isEqualTo(AnalysisStatus.ERRO_PARSE);
        assertThat(resultado.provider()).isEqualTo("gemini");
        assertThat(resultado.promptVersion()).isEqualTo("v1");
    }
}
