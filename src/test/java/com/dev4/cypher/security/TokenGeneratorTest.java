package com.dev4.cypher.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenGeneratorTest {

    @Test
    void tokenComecaComOPrefixoInformado() {
        String token = TokenGenerator.novo("est-tok");

        assertThat(token).startsWith("est-tok-");
        assertThat(token.length()).isGreaterThan("est-tok-".length());
    }

    @Test
    void doisTokensGeradosSaoDiferentes() {
        assertThat(TokenGenerator.novo("est-tok"))
                .isNotEqualTo(TokenGenerator.novo("est-tok"));
    }

    @Test
    void cemTokensGeradosSaoTodosUnicos() {
        Set<String> gerados = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            gerados.add(TokenGenerator.novo("cam-key"));
        }

        assertThat(gerados).hasSize(100);
    }
}
