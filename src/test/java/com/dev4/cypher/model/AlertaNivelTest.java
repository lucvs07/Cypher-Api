package com.dev4.cypher.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertaNivelTest {

    @ParameterizedTest
    @CsvSource({
            "0, BAIXO",
            "1, BAIXO",
            "2, MEDIO",
            "3, ALTO",
            "4, ALTO",
    })
    void derivaNivelAPartirDaSeveridade(int severidade, AlertaNivel esperado) {
        assertThat(AlertaNivel.fromSeverity(severidade)).isEqualTo(esperado);
    }

    @Test
    void rejeitaSeveridadeForaDaFaixa() {
        assertThatThrownBy(() -> AlertaNivel.fromSeverity(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AlertaNivel.fromSeverity(5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tiposDeIncidenteCobremQuedaEErgonomia() {
        assertThat(AlertaTipo.valueOf("FALL_RISK")).isNotNull();
        assertThat(AlertaTipo.valueOf("ERGONOMIC_RISK")).isNotNull();
    }
}
