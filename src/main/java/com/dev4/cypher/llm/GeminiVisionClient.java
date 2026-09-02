package com.dev4.cypher.llm;

import com.dev4.cypher.config.LlmProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "cypher.llm.provider", havingValue = "gemini")
public class GeminiVisionClient implements MultimodalRiskClient {

    private static final String MIME_TYPE_PADRAO = "image/jpeg";

    private final RestClient http;
    private final RestClient httpImagem;
    private final LlmProperties.ProviderConfig config;
    private final LlmProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiVisionClient(LlmProperties properties) {
        this.properties = properties;
        this.config = properties.getGemini();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));

        this.http = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .requestFactory(requestFactory)
                .build();

        this.httpImagem = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String analisar(String imagemUrl, String prompt) {
        ResponseEntity<byte[]> imagem = httpImagem.get()
                .uri(imagemUrl)
                .retrieve()
                .toEntity(byte[].class);

        String mimeType = tipoDeConteudoDaImagem(imagem.getHeaders());
        String dadosBase64 = Base64.getEncoder().encodeToString(imagem.getBody());

        Map<String, Object> corpo = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(
                                Map.of("text", prompt),
                                Map.of("inline_data", Map.of(
                                        "mime_type", mimeType,
                                        "data", dadosBase64))))),
                "generationConfig", Map.of("maxOutputTokens", properties.getMaxTokens()));

        String resposta = http.post()
                .uri("/v1beta/models/{model}:generateContent?key={apiKey}",
                        config.getModel(), config.getApiKey())
                .header("Content-Type", "application/json")
                .body(mapper.writeValueAsString(corpo))
                .retrieve()
                .body(String.class);

        JsonNode raiz = mapper.readTree(resposta);
        return raiz.path("candidates").path(0).path("content").path("parts").path(0)
                .path("text").asString("");
    }

    private String tipoDeConteudoDaImagem(HttpHeaders headers) {
        var contentType = headers.getContentType();
        if (contentType != null && "image".equalsIgnoreCase(contentType.getType())) {
            return contentType.toString();
        }
        return MIME_TYPE_PADRAO;
    }
}
