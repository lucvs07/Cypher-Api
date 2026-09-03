package com.dev4.cypher.service;

import com.dev4.cypher.dto.IncidentCloseRequest;
import com.dev4.cypher.dto.IncidentRequest;
import com.dev4.cypher.dto.IncidentResponse;
import com.dev4.cypher.event.IncidentChangedEvent;
import com.dev4.cypher.event.IncidentCreatedEvent;
import com.dev4.cypher.mapper.IncidentMapper;
import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.repository.AlertaRepository;
import com.dev4.cypher.security.TenantContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/** Ciclo de vida do incidente: abertura idempotente, fechamento e status. */
@Service
public class IncidentService {

    private final AlertaRepository repository;
    private final IncidentMapper mapper;
    private final ApplicationEventPublisher publisher;

    public IncidentService(AlertaRepository repository,
                           IncidentMapper mapper,
                           ApplicationEventPublisher publisher) {
        this.repository = repository;
        this.mapper = mapper;
        this.publisher = publisher;
    }

    /** @param created false quando a chave já existia (retry do pipeline). */
    public record IngestResult(IncidentResponse incident, boolean created) {
    }

    @Transactional
    public IngestResult ingest(IncidentRequest req) {
        // req.incidentKey() chega puro do payload; a chave armazenada é
        // prefixada pelo tenant da própria chave X-Cypher-Key que autenticou
        // esta ingestão (mesmo prefixo que IncidentMapper.toEntity aplica).
        String incidentKeyPrefixado = TenantContext.key(req.incidentKey());
        Optional<Alerta> existente = repository.findByIncidentKey(incidentKeyPrefixado);
        if (existente.isPresent()) {
            return new IngestResult(mapper.toResponse(existente.get()), false);
        }

        Alerta salvo;
        try {
            salvo = repository.saveAndFlush(mapper.toEntity(req));
        } catch (DataIntegrityViolationException corrida) {
            // Dois POSTs concorrentes com a mesma chave: quem perdeu relê o vencedor.
            Alerta vencedor = repository.findByIncidentKey(incidentKeyPrefixado)
                    .orElseThrow(() -> corrida);
            return new IngestResult(mapper.toResponse(vencedor), false);
        }

        IncidentResponse response = mapper.toResponse(salvo);
        publisher.publishEvent(new IncidentCreatedEvent(response));
        return new IngestResult(response, true);
    }

    /**
     * Leitura pura por incidentKey — usada para checar a câmera-alvo antes de
     * fechar (achado de segurança). {@code incidentKey} chega puro (é o que o
     * front/pipeline enxergam via {@link IncidentResponse#incidentKey()});
     * prefixado aqui pelo tenant atual antes da busca.
     */
    @Transactional(readOnly = true)
    public Optional<IncidentResponse> findByIncidentKey(String incidentKey) {
        return repository.findByIncidentKey(TenantContext.key(incidentKey)).map(mapper::toResponse);
    }

    @Transactional
    public Optional<IncidentResponse> close(String incidentKey, IncidentCloseRequest req) {
        return repository.findByIncidentKey(TenantContext.key(incidentKey)).map(alerta -> {
            // Cada campo é opcional e aplicado individualmente: o mesmo endpoint
            // serve ao fechamento do episódio e à chegada tardia do snapshot.
            if (req.endedAt() != null) {
                alerta.setEncerradoEm(req.endedAt());
            }
            if (req.durationSeconds() != null) {
                alerta.setDuracaoSegundos(req.durationSeconds());
            }
            if (req.peakSeverity() != null) {
                alerta.setSeveridadePico(req.peakSeverity());
            }
            if (req.closedReason() != null) {
                alerta.setClosedReason(req.closedReason());
            }
            if (req.snapshotUrl() != null) {
                alerta.setSnapshotUrl(req.snapshotUrl());
            }
            IncidentResponse response = mapper.toResponse(repository.saveAndFlush(alerta));
            publisher.publishEvent(new IncidentChangedEvent(response));
            return response;
        });
    }

    @Transactional(readOnly = true)
    public Page<IncidentResponse> search(OffsetDateTime since,
                                         String cameraId,
                                         Integer severityMin,
                                         AlertaStatus status,
                                         String estacaoId,
                                         Pageable pageable) {
        String empresaId = TenantContext.currentEmpresaId();
        String cameraIdPrefixado = cameraId == null ? null : TenantContext.key(empresaId, cameraId);
        String estacaoIdPrefixado = estacaoId == null ? null : TenantContext.key(empresaId, estacaoId);
        return repository.buscar(empresaId, since, cameraIdPrefixado, severityMin, status, estacaoIdPrefixado, pageable)
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<IncidentResponse> findById(Long id) {
        return repository.findById(id).map(mapper::toResponse);
    }

    @Transactional
    public Optional<IncidentResponse> changeStatus(Long id, AlertaStatus status) {
        return repository.findById(id).map(alerta -> {
            alerta.setStatus(status);
            IncidentResponse response = mapper.toResponse(repository.saveAndFlush(alerta));
            publisher.publishEvent(new IncidentChangedEvent(response));
            return response;
        });
    }
}
