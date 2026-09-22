package com.dev4.cypher.model;

/**
 * Por que o episódio foi encerrado.
 *
 * <p>TRACK_LOST e PIPELINE_STOPPED indicam duração de confiança menor: o risco
 * pode não ter cessado, o sistema é que deixou de observar. Ver spec §3.3.
 *
 * <p>RENOTIFIED espelha {@code cypher-capture/src/notify/incident.py}: a
 * violação persistiu além de {@code renotify_after_seconds} e o episódio foi
 * fechado e reaberto de propósito como incidente novo. Sem ele aqui, o PATCH
 * de fechamento voltava 400 e o incidente nunca fechava no painel.
 */
public enum ClosedReason {
    STATE_EXIT,
    ESCALATED,
    TRACK_LOST,
    PIPELINE_STOPPED,
    RENOTIFIED
}
