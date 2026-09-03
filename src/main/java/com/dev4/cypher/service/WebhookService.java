package com.dev4.cypher.service;

import com.dev4.cypher.catalog.CatalogValidationException;
import com.dev4.cypher.dto.WebhookDeliveryResponse;
import com.dev4.cypher.dto.WebhookRequest;
import com.dev4.cypher.dto.WebhookResponse;
import com.dev4.cypher.model.WebhookDelivery;
import com.dev4.cypher.model.WebhookSubscription;
import com.dev4.cypher.repository.WebhookDeliveryRepository;
import com.dev4.cypher.repository.WebhookSubscriptionRepository;
import com.dev4.cypher.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** CRUD de assinante de webhook (spec §2), escopado por empresa. */
@Service
public class WebhookService {

    private final WebhookSubscriptionRepository assinaturas;
    private final WebhookDeliveryRepository entregas;

    public WebhookService(WebhookSubscriptionRepository assinaturas, WebhookDeliveryRepository entregas) {
        this.assinaturas = assinaturas;
        this.entregas = entregas;
    }

    @Transactional(readOnly = true)
    public List<WebhookResponse> listar() {
        return assinaturas.findByEmpresaIdAndAtivoTrue(TenantContext.currentEmpresaId()).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public WebhookResponse criar(WebhookRequest req) {
        exigirUrlLivre(req.url(), null);
        WebhookSubscription nova = new WebhookSubscription(null, req.url(), req.secret(),
                req.minSeverity(), req.ativo() == null || req.ativo(), TenantContext.currentEmpresaId());
        return toResponse(assinaturas.saveAndFlush(nova));
    }

    @Transactional
    public Optional<WebhookResponse> atualizar(Long id, WebhookRequest req) {
        return doTenant(id).map(assinatura -> {
            exigirUrlLivre(req.url(), id);
            assinatura.setUrl(req.url());
            assinatura.setSecret(req.secret());
            assinatura.setMinSeverity(req.minSeverity());
            if (req.ativo() != null) {
                assinatura.setAtivo(req.ativo());
            }
            return toResponse(assinaturas.saveAndFlush(assinatura));
        });
    }

    @Transactional
    public boolean desativar(Long id) {
        return doTenant(id).map(assinatura -> {
            assinatura.setAtivo(false);
            assinaturas.saveAndFlush(assinatura);
            return true;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<Page<WebhookDeliveryResponse>> deliveries(Long id, Pageable pageable) {
        return doTenant(id).map(a -> entregas.findBySubscriptionId(id, pageable).map(this::toResponse));
    }

    /** Devolve a assinatura só se pertencer ao tenant atual — um id de outra empresa e tratado como inexistente. */
    Optional<WebhookSubscription> doTenant(Long id) {
        String empresaId = TenantContext.currentEmpresaId();
        return assinaturas.findById(id).filter(a -> a.getEmpresaId().equals(empresaId));
    }

    private void exigirUrlLivre(String url, Long idPropria) {
        assinaturas.findByUrl(url)
                .filter(outra -> !outra.getId().equals(idPropria))
                .ifPresent(outra -> {
                    throw new CatalogValidationException(HttpStatus.CONFLICT,
                            "URL ja usada por outro assinante: " + url);
                });
    }

    WebhookResponse toResponse(WebhookSubscription w) {
        return new WebhookResponse(w.getId(), w.getUrl(), w.getSecret(), w.getMinSeverity(), w.isAtivo());
    }

    private WebhookDeliveryResponse toResponse(WebhookDelivery d) {
        return new WebhookDeliveryResponse(d.getId(), d.getIncidentKey(), d.getStatus(),
                d.getTentativas(), d.getUltimoErro(), d.getProximaTentativa());
    }
}
