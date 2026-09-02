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
class ClaudeVisionClientTest {

    private static HttpServer servidor;
    private static final List<String> corposRecebidos = new CopyOnWriteArrayList<>();
    private static volatile int statusDeResposta = 200;
    private static volatile String corpoDeResposta =
            "{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"situacao\\\":\\\"ok\\\"}\"}]}";

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1/messages", troca -> {
            corposRecebidos.add(new String(troca.getRequestBody().readAllBytes()));
            byte[] resposta = corpoDeResposta.getBytes();
            troca.sendResponseHeaders(statusDeResposta, resposta.length);
            troca.getResponseBody().write(resposta);
            troca.close();
        });
        servidor.start();

        registry.add("cypher.llm.provider", () -> "claude");
        registry.add("cypher.llm.claude.base-url",
                () -> "http://127.0.0.1:" + servidor.getAddress().getPort());
        registry.add("cypher.llm.claude.api-key", () -> "chave-de-teste");
        registry.add("cypher.llm.claude.model", () -> "claude-teste");
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
        statusDeResposta = 200;
        corpoDeResposta = "{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"situacao\\\":\\\"ok\\\"}\"}]}";
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
