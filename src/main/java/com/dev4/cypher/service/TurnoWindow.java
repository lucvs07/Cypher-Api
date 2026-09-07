package com.dev4.cypher.service;

import com.dev4.cypher.model.Turno;

import java.time.OffsetDateTime;

/** Janela de turno fixa (spec §2.3) — constante de aplicação, não configurável por empresa nesta entrega. */
public final class TurnoWindow {

    public static final long DURACAO_NOMINAL_SEGUNDOS = 8 * 3600L;

    private TurnoWindow() {
    }

    public static Turno turnoDe(OffsetDateTime instante) {
        int hora = instante.getHour();
        if (hora >= 6 && hora < 14) {
            return Turno.MANHA;
        }
        if (hora >= 14 && hora < 22) {
            return Turno.TARDE;
        }
        return Turno.NOITE;
    }
}
