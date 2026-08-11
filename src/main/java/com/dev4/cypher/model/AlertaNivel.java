package com.dev4.cypher.model;

/**
 * Nível legível para humanos, derivado da severidade 0–4 do pipeline.
 *
 * <p>A severidade crua é preservada no incidente; este enum existe para
 * leitura e compatibilidade com o vocabulário anterior do projeto.
 */
public enum AlertaNivel {
    BAIXO,
    MEDIO,
    ALTO;

    public static AlertaNivel fromSeverity(int severidade) {
        if (severidade < 0 || severidade > 4) {
            throw new IllegalArgumentException(
                    "Severidade fora da faixa 0-4: " + severidade);
        }
        if (severidade <= 1) {
            return BAIXO;
        }
        if (severidade == 2) {
            return MEDIO;
        }
        return ALTO;
    }
}
