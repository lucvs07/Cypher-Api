package com.dev4.cypher.llm;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Retry com backoff exponencial + jitter para chamadas a provedores de LLM.
 *
 * So tenta de novo em falha transitoria (5xx, 429, timeout/rede) — um erro
 * de chave invalida ou request malformado (4xx que nao seja 429) nunca e
 * retentado, porque tentar de novo nao vai mudar o resultado.
 *
 * A funcao de "dormir" e injetavel para permitir testes rapidos sem sleep
 * real (mesmo principio do resto do projeto: transporte/efeito colateral
 * isolado atras de uma interface simples).
 */
public class RetryComBackoff {

    private final int maxTentativas;
    private final Duration esperaBase;
    private final LongConsumer dormir;

    public RetryComBackoff(int maxTentativas, Duration esperaBase) {
        this(maxTentativas, esperaBase, RetryComBackoff::dormirDeVerdade);
    }

    RetryComBackoff(int maxTentativas, Duration esperaBase, LongConsumer dormir) {
        this.maxTentativas = maxTentativas;
        this.esperaBase = esperaBase;
        this.dormir = dormir;
    }

    public <T> T executar(Supplier<T> chamada, Predicate<Exception> tentavelDeNovo) {
        for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {
            try {
                return chamada.get();
            } catch (Exception falha) {
                boolean ultimaTentativa = tentativa == maxTentativas;
                if (ultimaTentativa || !tentavelDeNovo.test(falha)) {
                    throw falha;
                }
                dormir.accept(esperaComJitter(tentativa));
            }
        }
        throw new IllegalStateException("inalcancavel");
    }

    /** 1s, 2s, 4s... com jitter de +-25% para nao sincronizar retries em rajada. */
    private long esperaComJitter(int tentativa) {
        long baseMillis = esperaBase.toMillis() * (1L << (tentativa - 1));
        long amplitudeJitter = baseMillis / 4;
        long jitter = amplitudeJitter == 0 ? 0
                : ThreadLocalRandom.current().nextLong(-amplitudeJitter, amplitudeJitter + 1);
        return Math.max(0, baseMillis + jitter);
    }

    /** Classificacao padrao: 5xx, 429 e falha de rede/timeout sao transitorios. */
    public static boolean transitoria(Exception falha) {
        if (falha instanceof HttpServerErrorException) {
            return true;
        }
        if (falha instanceof HttpClientErrorException.TooManyRequests) {
            return true;
        }
        if (falha instanceof ResourceAccessException) {
            return true;
        }
        return false;
    }

    private static void dormirDeVerdade(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrompida) {
            Thread.currentThread().interrupt();
        }
    }
}
