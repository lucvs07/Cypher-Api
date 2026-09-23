package com.dev4.cypher.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EpiTipoLegadoTest {

    @ParameterizedTest
    @CsvSource({
            "HELMET,HELMET", "helmet,HELMET",       // canonico, qualquer caixa
            "Goggles,GOGGLES", "boots,BOOTS",       // classe crua do detector
            "no_goggle,GOGGLES", "no_gloves,GLOVE", // rotulo de violacao do pipeline
            "capacete,HELMET", "botas,BOOTS"        // chave PT do onboarding
    })
    void leFormatoLegadoComoCanonico(String gravado, EpiTipo esperado) {
        assertThat(EpiTipoLegado.ler(gravado)).isEqualTo(esperado);
    }

    @Test
    void naoDetectavelEDescartado() {
        assertThat(EpiTipoLegado.ler("auricular")).isNull();
        assertThat(EpiTipoLegado.lerTodos(List.of("cinto", "no_helmet", "HELMET", "lixo")))
                .containsExactly(EpiTipo.HELMET);
    }

    @Test
    void gravacaoESempreONomeCanonico() {
        assertThat(EpiTipoLegado.gravar(List.of(EpiTipo.GOGGLES, EpiTipo.GLOVE)))
                .containsExactly("GOGGLES", "GLOVE");
        assertThat(EpiTipoLegado.gravar(null)).isEmpty();
    }
}
