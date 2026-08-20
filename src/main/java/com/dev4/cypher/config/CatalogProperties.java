package com.dev4.cypher.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Catálogo de estações e câmeras vindo de configuração.
 *
 * <p>Em E1 esta é a única origem do catálogo; E2 substitui por cadastro na
 * interface. Nada aqui autoriza ingestão: um incidente de câmera não
 * cadastrada é aceito do mesmo jeito (spec §12).
 */
@Component
@ConfigurationProperties(prefix = "cypher.catalog")
@Getter
@Setter
public class CatalogProperties {

    private List<StationConfig> stations = new ArrayList<>();
    private List<CameraConfig> cameras = new ArrayList<>();
    private List<WebhookConfig> webhooks = new ArrayList<>();

    @Getter
    @Setter
    public static class StationConfig {
        private String id;
        private String nome;
        private String setor;
        private boolean ativa = true;
    }

    @Getter
    @Setter
    public static class CameraConfig {
        private String id;
        private String nome;
        private String estacaoId;
        private String streamUrl;
        private String fonte;
        private boolean ativa = true;
    }

    @Getter
    @Setter
    public static class WebhookConfig {
        private String url;
        private String secret;
        private Integer minSeverity = 3;
        private boolean ativo = true;
    }
}
