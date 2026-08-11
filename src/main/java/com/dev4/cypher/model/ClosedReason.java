package com.dev4.cypher.model;

/**
 * Por que o episódio foi encerrado.
 *
 * <p>TRACK_LOST e PIPELINE_STOPPED indicam duração de confiança menor: o risco
 * pode não ter cessado, o sistema é que deixou de observar. Ver spec §3.3.
 */
public enum ClosedReason {
    STATE_EXIT,
    ESCALATED,
    TRACK_LOST,
    PIPELINE_STOPPED
}
