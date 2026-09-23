package com.dev4.cypher.llm;

import com.dev4.cypher.model.EpiTipo;

import com.dev4.cypher.model.Alerta;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    private Alerta alertaDeExemplo() {
        Alerta alerta = new Alerta();
        alerta.setPosturaCategoria("critical_zone");
        alerta.setZona("prensa-01");
        alerta.setEpiPresent(List.of(EpiTipo.HELMET));
        alerta.setEpiMissing(List.of(EpiTipo.GLOVE));
        alerta.setRebaScore(9);
        alerta.setSeveridade(3);
        alerta.setDataHora(OffsetDateTime.now());
        return alerta;
    }

    @Test
    void v1NaoMencionaSinaisDaVisaoComputacional() {
        String prompt = builder.montar(alertaDeExemplo(), "v1");

        assertThat(prompt).doesNotContain("prensa-01");
        assertThat(prompt).contains("JSON");
    }

    @Test
    void v2IncluiSinaisJaCalculadosPelaVisaoComputacional() {
        String prompt = builder.montar(alertaDeExemplo(), "v2");

        assertThat(prompt).contains("critical_zone");
        assertThat(prompt).contains("prensa-01");
        assertThat(prompt).contains("EPIs presentes: HELMET");
        assertThat(prompt).contains("EPIs ausentes: GLOVE");
        assertThat(prompt).contains("JSON");
    }

    @Test
    void versaoDesconhecidaCaiParaV2() {
        String prompt = builder.montar(alertaDeExemplo(), "qualquer-coisa");

        assertThat(prompt).contains("prensa-01");
    }
}
