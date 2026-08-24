package com.dev4.cypher.security;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Gerador único de token opaco, compartilhado por `Estacao.token` e
 * `Camera.apiKey` (spec E4 §2.3/§2.4) — 24 bytes de `SecureRandom` em hex
 * (48 caracteres), prefixados para que o valor seja identificável a olho nu
 * (`est-tok-...`, `cam-key-...`) sem revelar nada sobre o algoritmo.
 */
public final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BYTES = 24;

    private TokenGenerator() {
    }

    public static String novo(String prefixo) {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return prefixo + "-" + HexFormat.of().formatHex(bytes);
    }
}
