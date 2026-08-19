package com.dev4.cypher.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {

    private final WebhookSigner signer = new WebhookSigner();

    @Test
    void assinaturaEhDeterministica() {
        String a = signer.sign("segredo", "1786000000", "{\"x\":1}");
        String b = signer.sign("segredo", "1786000000", "{\"x\":1}");

        assertThat(a).isEqualTo(b);
        assertThat(a).startsWith("sha256=");
    }

    @Test
    void corpoDiferenteProduzAssinaturaDiferente() {
        String a = signer.sign("segredo", "1786000000", "{\"x\":1}");
        String b = signer.sign("segredo", "1786000000", "{\"x\":2}");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void timestampEntraNaAssinaturaParaImpedirReplay() {
        String a = signer.sign("segredo", "1786000000", "{\"x\":1}");
        String b = signer.sign("segredo", "1786000099", "{\"x\":1}");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void segredoDiferenteProduzAssinaturaDiferente() {
        String a = signer.sign("segredo-a", "1786000000", "{\"x\":1}");
        String b = signer.sign("segredo-b", "1786000000", "{\"x\":1}");

        assertThat(a).isNotEqualTo(b);
    }
}
