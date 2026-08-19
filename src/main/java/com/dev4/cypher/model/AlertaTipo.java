package com.dev4.cypher.model;

/**
 * Natureza do incidente detectado.
 *
 * <p>EPI_VIOLATION existe no contrato mas não é gerado nesta entrega: o modelo
 * de detecção só possui classes de presença de EPI, sem classes de violação.
 * Ver spec §4.6.
 */
public enum AlertaTipo {
    FALL_RISK,
    ERGONOMIC_RISK,
    ZONE_VIOLATION,
    EPI_VIOLATION
}
