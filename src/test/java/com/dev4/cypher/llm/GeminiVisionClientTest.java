package com.dev4.cypher.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GeminiVisionClientTest {

    private static HttpServer servidor;
    private static final List<String> corposRecebidos = new CopyOnWriteArrayList<>();
    private static final byte[] BYTES_DA_IMAGEM_FALSA = "bytes-de-imagem-falsa".getBytes();
    private static volatile String corpoDeResposta =
            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"situacao\\\":\\\"ok\\\"}\"}]}}]}";
    private static String urlDaImagem;

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1beta/", troca -> {
            corposRecebidos.add(new String(troca.getRequestBody().readAllBytes()));
            byte[] resposta = corpoDeResposta.getBytes();
            troca.sendResponseHeaders(200, resposta.length);
            troca.getResponseBody().write(resposta);
            troca.close();
        });
        servidor.createContext("/imagem.jpg", troca -> {
            troca.getResponseHeaders().add("Content-Type", "image/jpeg");
            troca.sendResponseHeaders(200, BYTES_DA_IMAGEM_FALSA.length);
            troca.getResponseBody().write(BYTES_DA_IMAGEM_FALSA);
            troca.close();
        });
        servidor.start();

        urlDaImagem = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/imagem.jpg";

        registry.add("cypher.llm.provider", () -> "gemini");
        registry.add("cypher.llm.gemini.base-url",
                () -> "http://127.0.0.1:" + servidor.getAddress().getPort());
        registry.add("cypher.llm.gemini.api-key", () -> "chave-de-teste");
        registry.add("cypher.llm.gemini.model", () -> "gemini-teste");
    }

    @AfterAll
    static void derrubarServidor() {
        servidor.stop(0);
    }

    @Autowired
    private MultimodalRiskClient client;

    @BeforeEach
    void limpar() {
        corposRecebidos.clear();
    }

    @Test
    void enviaImagemComoInlineDataEDevolveOTextoDaResposta() {
        String resultado = client.analisar(urlDaImagem, "analise isso");

        assertThat(corposRecebidos).hasSize(1);
        assertThat(corposRecebidos.get(0)).contains("analise isso");
        assertThat(corposRecebidos.get(0)).contains("inline_data");
        assertThat(corposRecebidos.get(0))
                .contains(Base64.getEncoder().encodeToString(BYTES_DA_IMAGEM_FALSA));
        assertThat(corposRecebidos.get(0)).doesNotContain("file_data");
        assertThat(corposRecebidos.get(0)).doesNotContain("file_uri");
        assertThat(resultado).contains("situacao");
    }
}
