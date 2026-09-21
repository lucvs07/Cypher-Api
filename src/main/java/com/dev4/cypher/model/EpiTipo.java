package com.dev4.cypher.model;

/**
 * Catálogo canônico de EPI — espelha ``cypher-capture/src/pose/epi_catalog.py``.
 *
 * <p>Existiam 3 convenções soltas antes disto (achado 2026-09-20): classe do
 * detector (inglês, {@code HELMET}/{@code Goggles}/{@code boots}, maiúsculas
 * inconsistentes), label de violação ({@code no_helmet}, nunca gerado de
 * verdade — o modelo só tem classes de presença) e nome livre em português
 * usado em {@code Zona.requiredPpe}/checklist do onboarding ({@code
 * "capacete"}, nunca validado). O nome do enum é o formato que trafega entre
 * backend e pipeline (JSON); o rótulo amigável em português é responsabilidade
 * exclusiva do front, nunca deste enum.
 */
public enum EpiTipo {
    HELMET,
    GOGGLES,
    GLOVE,
    VEST,
    BOOTS,
    MASK
}
