package com.dev4.cypher.service;

import com.dev4.cypher.catalog.CatalogValidationException;
import com.dev4.cypher.dto.TeamRequest;
import com.dev4.cypher.dto.TeamResponse;
import com.dev4.cypher.model.Equipe;
import com.dev4.cypher.model.Turno;
import com.dev4.cypher.repository.EquipeRepository;
import com.dev4.cypher.repository.EstacaoRepository;
import com.dev4.cypher.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** CRUD de equipe e alocação de estações/turno (spec E4 §3). */
@Service
public class EquipeService {

    private final EquipeRepository equipes;
    private final EstacaoRepository estacoes;

    public EquipeService(EquipeRepository equipes, EstacaoRepository estacoes) {
        this.equipes = equipes;
        this.estacoes = estacoes;
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> listar() {
        return equipes.findByEmpresaId(TenantContext.currentEmpresaId()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public TeamResponse criar(TeamRequest req) {
        String id = TenantContext.key(req.id());
        if (equipes.existsById(id)) {
            throw new CatalogValidationException(HttpStatus.CONFLICT, "Equipe ja cadastrada: " + req.id());
        }
        List<String> estacaoIds = prefixar(req.estacaoIds());
        exigirEstacoesExistentes(estacaoIds);
        exigirSemConflito(estacaoIds, req.turno(), null);
        Equipe nova = new Equipe(id, req.nome(), req.turno(), estacaoIds,
                req.ativa() == null || req.ativa(), TenantContext.currentEmpresaId());
        return toResponse(equipes.saveAndFlush(nova));
    }

    @Transactional
    public Optional<TeamResponse> atualizar(String id, TeamRequest req) {
        exigirMesmoCodigo(id, req.id());
        return equipes.findById(TenantContext.key(id)).map(equipe -> {
            List<String> estacaoIds = prefixar(req.estacaoIds());
            exigirEstacoesExistentes(estacaoIds);
            exigirSemConflito(estacaoIds, req.turno(), equipe.getId());
            equipe.setNome(req.nome());
            equipe.setTurno(req.turno());
            equipe.setEstacaoIds(estacaoIds);
            if (req.ativa() != null) {
                equipe.setAtiva(req.ativa());
            }
            return toResponse(equipes.saveAndFlush(equipe));
        });
    }

    @Transactional
    public boolean remover(String id) {
        String chave = TenantContext.key(id);
        if (!equipes.existsById(chave)) {
            return false;
        }
        equipes.deleteById(chave);
        return true;
    }

    private List<String> prefixar(List<String> estacaoIds) {
        String empresaId = TenantContext.currentEmpresaId();
        return estacaoIds.stream()
                .map(id -> TenantContext.key(empresaId, id))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    private void exigirEstacoesExistentes(List<String> estacaoIdsPrefixados) {
        for (String estacaoId : estacaoIdsPrefixados) {
            if (!estacoes.existsById(estacaoId)) {
                throw new CatalogValidationException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Estacao inexistente: " + TenantContext.codigo(estacaoId));
            }
        }
    }

    /** (estacaoId, turno) e unico a nivel de aplicacao (spec E4 §3) — nunca constraint de banco. */
    private void exigirSemConflito(List<String> estacaoIdsPrefixados, Turno turno, String idPropria) {
        equipes.findByEmpresaIdAndTurno(TenantContext.currentEmpresaId(), turno).stream()
                .filter(outra -> !outra.getId().equals(idPropria))
                .forEach(outra -> {
                    for (String estacaoId : estacaoIdsPrefixados) {
                        if (outra.getEstacaoIds().contains(estacaoId)) {
                            throw new CatalogValidationException(HttpStatus.CONFLICT,
                                    "Estacao %s ja coberta pela equipe %s no turno %s"
                                            .formatted(TenantContext.codigo(estacaoId),
                                                    TenantContext.codigo(outra.getId()), turno));
                        }
                    }
                });
    }

    private void exigirMesmoCodigo(String naUrl, String noCorpo) {
        if (noCorpo != null && !noCorpo.equals(naUrl)) {
            throw new CatalogValidationException(HttpStatus.CONFLICT,
                    "O codigo de equipe e imutavel: %s nao pode virar %s".formatted(naUrl, noCorpo));
        }
    }

    private TeamResponse toResponse(Equipe e) {
        List<String> estacaoIds = e.getEstacaoIds().stream().map(TenantContext::codigo).toList();
        return new TeamResponse(TenantContext.codigo(e.getId()), e.getNome(), e.getTurno(), estacaoIds, e.isAtiva());
    }
}
