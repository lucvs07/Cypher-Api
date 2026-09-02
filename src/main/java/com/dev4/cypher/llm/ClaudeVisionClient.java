package com.dev4.cypher.llm;

import com.dev4.cypher.config.LlmProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "cypher.llm.provider", havingValue = "claude")
public class ClaudeVisionClient implements MultimodalRiskClient {

    private final RestClient http;
    private final LlmProperties.ProviderConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    public ClaudeVisionClient(LlmProperties properties) {
        this.config = properties.getClaude();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));

        this.http = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String analisar(String imagemUrl, String prompt) {
        Map<String, Object> corpo = Map.of(
                "model", config.getModel(),
                "max_tokens", 1024,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "image", "source", Map.of("type", "url", "url", imagemUrl)),
                                Map.of("type", "text", "text", prompt)))));

        String resposta = http.post()
                .uri("/v1/messages")
                .header("x-api-key", config.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .body(mapper.writeValueAsString(corpo))
                .retrieve()
                .body(String.class);

        JsonNode raiz = mapper.readTree(resposta);
        return raiz.path("content").path(0).path("text").asText("");
    }
}
