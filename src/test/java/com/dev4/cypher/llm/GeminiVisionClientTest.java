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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GeminiVisionClientTest {

    private static HttpServer servidor;
    private static final List<String> corposRecebidos = new CopyOnWriteArrayList<>();
    private static volatile String corpoDeResposta =
            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"situacao\\\":\\\"ok\\\"}\"}]}}]}";

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            corposRecebidos.add(new String(troca.getRequestBody().readAllBytes()));
            byte[] resposta = corpoDeResposta.getBytes();
            troca.sendResponseHeaders(200, resposta.length);
            troca.getResponseBody().write(resposta);
            troca.close();
        });
        servidor.start();

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
    void enviaImagemEPromptEDevolveOTextoDaResposta() {
        String resultado = client.analisar("https://exemplo.com/foto.jpg", "analise isso");

        assertThat(corposRecebidos).hasSize(1);
        assertThat(corposRecebidos.get(0)).contains("https://exemplo.com/foto.jpg");
        assertThat(corposRecebidos.get(0)).contains("analise isso");
        assertThat(resultado).contains("situacao");
    }
}
