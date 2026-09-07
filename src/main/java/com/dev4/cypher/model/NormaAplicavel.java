package com.dev4.cypher.model;

import java.util.Map;

/** Mapeamento regra->norma regulamentadora (spec E5 §4) — constante, sem persistência própria. */
public final class NormaAplicavel {

    private static final Map<AlertaTipo, String> NORMAS = Map.of(
            AlertaTipo.FALL_RISK, "NR-35",
            AlertaTipo.ERGONOMIC_RISK, "NR-17",
            AlertaTipo.ZONE_VIOLATION, "NR-12",
            AlertaTipo.EPI_VIOLATION, "NR-6"
    );

    private NormaAplicavel() {
    }

    public static String de(AlertaTipo tipo) {
        return NORMAS.get(tipo);
    }
}
