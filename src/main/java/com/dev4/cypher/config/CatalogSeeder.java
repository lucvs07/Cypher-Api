package com.dev4.cypher.config;

import com.dev4.cypher.model.Camera;
import com.dev4.cypher.model.Estacao;
import com.dev4.cypher.model.WebhookSubscription;
import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.repository.EstacaoRepository;
import com.dev4.cypher.repository.WebhookSubscriptionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Aplica o catálogo de configuração ao banco no boot, por upsert: reiniciar a
 * aplicação não duplica registros nem apaga o que já existe.
 */
@Component
public class CatalogSeeder implements ApplicationRunner {

    private final CatalogProperties properties;
    private final EstacaoRepository estacoes;
    private final CameraRepository cameras;
    private final WebhookSubscriptionRepository webhooks;

    public CatalogSeeder(CatalogProperties properties,
                         EstacaoRepository estacoes,
                         CameraRepository cameras,
                         WebhookSubscriptionRepository webhooks) {
        this.properties = properties;
        this.estacoes = estacoes;
        this.cameras = cameras;
        this.webhooks = webhooks;
    }

    @Override
    public void run(ApplicationArguments args) {
        properties.getStations().forEach(cfg ->
                estacoes.save(new Estacao(cfg.getId(), cfg.getNome(), cfg.getSetor(), cfg.isAtiva())));

        properties.getCameras().forEach(cfg ->
                cameras.save(new Camera(cfg.getId(), cfg.getNome(), cfg.getEstacaoId(),
                        cfg.getStreamUrl(), cfg.getFonte(), cfg.isAtiva())));

        properties.getWebhooks().forEach(cfg -> {
            boolean jaExiste = webhooks.findAll().stream()
                    .anyMatch(w -> w.getUrl().equals(cfg.getUrl()));
            if (!jaExiste) {
                webhooks.save(new WebhookSubscription(null, cfg.getUrl(), cfg.getSecret(),
                        cfg.getMinSeverity(), cfg.isAtivo()));
            }
        });
    }
}
