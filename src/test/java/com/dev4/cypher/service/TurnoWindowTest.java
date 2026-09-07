package com.dev4.cypher.service;

import com.dev4.cypher.model.Turno;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TurnoWindowTest {

    @Test
    void seisEQuinzeCentesimosEhManha() {
        assertThat(TurnoWindow.turnoDe(instante(6, 0))).isEqualTo(Turno.MANHA);
        assertThat(TurnoWindow.turnoDe(instante(13, 59))).isEqualTo(Turno.MANHA);
    }

    @Test
    void quatorzeEhTarde() {
        assertThat(TurnoWindow.turnoDe(instante(14, 0))).isEqualTo(Turno.TARDE);
        assertThat(TurnoWindow.turnoDe(instante(21, 59))).isEqualTo(Turno.TARDE);
    }

    @Test
    void vinteEDoisEMeiaNoiteSaoNoite() {
        assertThat(TurnoWindow.turnoDe(instante(22, 0))).isEqualTo(Turno.NOITE);
        assertThat(TurnoWindow.turnoDe(instante(0, 0))).isEqualTo(Turno.NOITE);
        assertThat(TurnoWindow.turnoDe(instante(5, 59))).isEqualTo(Turno.NOITE);
    }

    @Test
    void duracaoNominalEhOitoHoras() {
        assertThat(TurnoWindow.DURACAO_NOMINAL_SEGUNDOS).isEqualTo(8 * 3600L);
    }

    private OffsetDateTime instante(int hora, int minuto) {
        return OffsetDateTime.of(2026, 8, 29, hora, minuto, 0, 0, ZoneOffset.UTC);
    }
}
