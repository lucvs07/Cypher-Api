package com.dev4.cypher.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Origens autorizadas a falar com a API.
 *
 * <p>Precisa ser {@code @ConfigurationProperties} e não {@code @Value}: uma
 * lista YAML chega ao ambiente como chaves indexadas
 * ({@code ...allowed-origins[0]}), que {@code ${...}} não resolve. A ligação
 * relaxada aceita as duas formas — lista YAML e string separada por vírgula.
 */
@Component
@ConfigurationProperties(prefix = "cypher.cors")
@Getter
@Setter
public class CorsProperties {

    private List<String> allowedOrigins = new ArrayList<>();
}
