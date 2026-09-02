package com.dev4.cypher.config;

import com.dev4.cypher.model.Camera;
import com.dev4.cypher.model.Empresa;
import com.dev4.cypher.model.Estacao;
import com.dev4.cypher.model.WebhookSubscription;
import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.repository.EmpresaRepository;
import com.dev4.cypher.repository.EstacaoRepository;
import com.dev4.cypher.repository.WebhookSubscriptionRepository;
import com.dev4.cypher.security.TenantContext;
import com.dev4.cypher.security.TokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Aplica o catálogo de configuração ao banco no boot, por upsert: reiniciar a
 * aplicação não duplica registros nem apaga o que já existe.
 */
@Component
public class CatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);

    private final CatalogProperties properties;
    private final EmpresaRepository empresas;
    private final EstacaoRepository estacoes;
    private final CameraRepository cameras;
    private final WebhookSubscriptionRepository webhooks;

    public CatalogSeeder(CatalogProperties properties,
                         EmpresaRepository empresas,
                         EstacaoRepository estacoes,
                         CameraRepository cameras,
                         WebhookSubscriptionRepository webhooks) {
        this.properties = properties;
        this.empresas = empresas;
        this.estacoes = estacoes;
        this.cameras = cameras;
        this.webhooks = webhooks;
    }

    /**
     * Bootstrap, não sincronização: popula somente quando a tabela está vazia.
     *
     * <p>Antes de E2 isto era upsert a cada boot, o que significa que — com o
     * cadastro pela interface existindo — editar uma estação e reiniciar a
     * aplicação desfaria a edição em silêncio.
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!empresas.existsById(TenantContext.DEMO_EMPRESA_ID)) {
            empresas.save(new Empresa(TenantContext.DEMO_EMPRESA_ID, "Empresa de demonstracao", true));
        }

        if (estacoes.count() == 0) {
            properties.getStations().forEach(cfg ->
                    estacoes.save(new Estacao(TenantContext.key(TenantContext.DEMO_EMPRESA_ID, cfg.getId()),
                            cfg.getNome(), cfg.getSetor(), cfg.isAtiva(), TokenGenerator.novo("est-tok"),
                            TenantContext.DEMO_EMPRESA_ID)));
            log.info("Catalogo: {} estacao(oes) semeada(s) da configuracao",
                    properties.getStations().size());
        } else {
            log.info("Catalogo: {} estacao(oes) ja no banco, configuracao ignorada",
                    estacoes.count());
        }

        if (cameras.count() == 0) {
            properties.getCameras().forEach(cfg ->
                    cameras.save(new Camera(TenantContext.key(TenantContext.DEMO_EMPRESA_ID, cfg.getId()),
                            cfg.getNome(), TenantContext.key(TenantContext.DEMO_EMPRESA_ID, cfg.getEstacaoId()),
                            cfg.getStreamUrl(), cfg.getFonte(), cfg.isAtiva(), TokenGenerator.novo("cam-key"),
                            TenantContext.DEMO_EMPRESA_ID)));
            log.info("Catalogo: {} camera(s) semeada(s) da configuracao",
                    properties.getCameras().size());
        } else {
            log.info("Catalogo: {} camera(s) ja no banco, configuracao ignorada",
                    cameras.count());
        }

        // Assinaturas de webhook seguem por URL: são infraestrutura de
        // integração, não cadastro editável pela interface.
        properties.getWebhooks().forEach(cfg -> {
            boolean jaExiste = webhooks.findAll().stream()
                    .anyMatch(w -> w.getUrl().equals(cfg.getUrl()));
            if (!jaExiste) {
                webhooks.save(new WebhookSubscription(null, cfg.getUrl(), cfg.getSecret(),
                        cfg.getMinSeverity(), cfg.isAtivo(), TenantContext.DEMO_EMPRESA_ID));
            }
        });
    }
}
