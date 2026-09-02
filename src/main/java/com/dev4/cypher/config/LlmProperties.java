package com.dev4.cypher.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Config de análise de risco por LLM multimodal (spec §2.7). */
@Component
@ConfigurationProperties(prefix = "cypher.llm")
@Getter
@Setter
public class LlmProperties {
    private String provider = "claude";
    private String promptVersion = "v2";
    private int timeoutSeconds = 20;
    private ProviderConfig claude = new ProviderConfig();
    private ProviderConfig gemini = new ProviderConfig();

    @Getter
    @Setter
    public static class ProviderConfig {
        private String apiKey;
        private String model;
        private String baseUrl;
    }
}
