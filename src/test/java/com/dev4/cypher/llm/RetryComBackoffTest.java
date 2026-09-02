package com.dev4.cypher.llm;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryComBackoffTest {

    @Test
    void sucessoNaPrimeiraTentativaNaoDorme() {
        List<Long> dormidas = new ArrayList<>();
        RetryComBackoff retry = new RetryComBackoff(3, Duration.ofSeconds(1), dormidas::add);

        String resultado = retry.executar(() -> "ok", RetryComBackoff::transitoria);

        assertThat(resultado).isEqualTo("ok");
        assertThat(dormidas).isEmpty();
    }

    @Test
    void tentaDeNovoEmFalhaTransitoriaEDormeComBackoffExponencial() {
        List<Long> dormidas = new ArrayList<>();
        RetryComBackoff retry = new RetryComBackoff(3, Duration.ofSeconds(1), dormidas::add);
        AtomicInteger chamadas = new AtomicInteger(0);

        String resultado = retry.executar(() -> {
            int tentativa = chamadas.incrementAndGet();
            if (tentativa < 3) {
                throw HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE,
                        "Service Unavailable", null, null, null);
            }
            return "ok";
        }, RetryComBackoff::transitoria);

        assertThat(resultado).isEqualTo("ok");
        assertThat(chamadas.get()).isEqualTo(3);
        // Backoff exponencial: base 1s, depois 2s. Jitter de +-25% mantem
        // cada espera dentro de uma faixa em torno do valor base.
        assertThat(dormidas).hasSize(2);
        assertThat(dormidas.get(0)).isBetween(750L, 1250L);
        assertThat(dormidas.get(1)).isBetween(1500L, 2500L);
    }

    @Test
    void naoTentaDeNovoEmErroNaoTransitorio() {
        List<Long> dormidas = new ArrayList<>();
        RetryComBackoff retry = new RetryComBackoff(3, Duration.ofSeconds(1), dormidas::add);
        AtomicInteger chamadas = new AtomicInteger(0);

        assertThatThrownBy(() -> retry.executar(() -> {
            chamadas.incrementAndGet();
            throw HttpClientErrorException.create(HttpStatus.UNAUTHORIZED,
                    "Unauthorized", null, null, null);
        }, RetryComBackoff::transitoria)).isInstanceOf(HttpClientErrorException.class);

        assertThat(chamadas.get()).isEqualTo(1);
        assertThat(dormidas).isEmpty();
    }

    @Test
    void esgotaTentativasELancaAUltimaFalha() {
        List<Long> dormidas = new ArrayList<>();
        RetryComBackoff retry = new RetryComBackoff(3, Duration.ofSeconds(1), dormidas::add);
        AtomicInteger chamadas = new AtomicInteger(0);

        assertThatThrownBy(() -> retry.executar(() -> {
            chamadas.incrementAndGet();
            throw HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE,
                    "Service Unavailable", null, null, null);
        }, RetryComBackoff::transitoria)).isInstanceOf(HttpServerErrorException.class);

        assertThat(chamadas.get()).isEqualTo(3);
        assertThat(dormidas).hasSize(2);
    }

    @Test
    void classificaTransitoriaCorretamente() {
        assertThat(RetryComBackoff.transitoria(HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "x", null, null, null))).isTrue();
        assertThat(RetryComBackoff.transitoria(HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "x", null, null, null))).isTrue();
        assertThat(RetryComBackoff.transitoria(
                new org.springframework.web.client.ResourceAccessException("timeout"))).isTrue();
        assertThat(RetryComBackoff.transitoria(HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "x", null, null, null))).isFalse();
        assertThat(RetryComBackoff.transitoria(new IllegalStateException("bug"))).isFalse();
    }
}
