package com.dev4.cypher.service;

import com.dev4.cypher.catalog.CatalogValidationException;
import com.dev4.cypher.dto.TeamRequest;
import com.dev4.cypher.dto.TeamResponse;
import com.dev4.cypher.model.Equipe;
import com.dev4.cypher.model.Turno;
import com.dev4.cypher.repository.EquipeRepository;
import com.dev4.cypher.repository.EstacaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
        return equipes.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public TeamResponse criar(TeamRequest req) {
        if (equipes.existsById(req.id())) {
            throw new CatalogValidationException(HttpStatus.CONFLICT, "Equipe ja cadastrada: " + req.id());
        }
        exigirEstacoesExistentes(req.estacaoIds());
        exigirSemConflito(req.estacaoIds(), req.turno(), null);
        Equipe nova = new Equipe(req.id(), req.nome(), req.turno(), new ArrayList<>(req.estacaoIds()),
                req.ativa() == null || req.ativa());
        return toResponse(equipes.saveAndFlush(nova));
    }

    @Transactional
    public Optional<TeamResponse> atualizar(String id, TeamRequest req) {
        exigirMesmoCodigo(id, req.id());
        return equipes.findById(id).map(equipe -> {
            exigirEstacoesExistentes(req.estacaoIds());
            exigirSemConflito(req.estacaoIds(), req.turno(), id);
            equipe.setNome(req.nome());
            equipe.setTurno(req.turno());
            equipe.setEstacaoIds(new ArrayList<>(req.estacaoIds()));
            if (req.ativa() != null) {
                equipe.setAtiva(req.ativa());
            }
            return toResponse(equipes.saveAndFlush(equipe));
        });
    }

    @Transactional
    public boolean remover(String id) {
        if (!equipes.existsById(id)) {
            return false;
        }
        equipes.deleteById(id);
        return true;
    }

    private void exigirEstacoesExistentes(List<String> estacaoIds) {
        for (String estacaoId : estacaoIds) {
            if (!estacoes.existsById(estacaoId)) {
                throw new CatalogValidationException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Estacao inexistente: " + estacaoId);
            }
        }
    }

    /** (estacaoId, turno) e unico a nivel de aplicacao (spec E4 §3) — nunca constraint de banco. */
    private void exigirSemConflito(List<String> estacaoIds, Turno turno, String idPropria) {
        equipes.findByTurno(turno).stream()
                .filter(outra -> !outra.getId().equals(idPropria))
                .forEach(outra -> {
                    for (String estacaoId : estacaoIds) {
                        if (outra.getEstacaoIds().contains(estacaoId)) {
                            throw new CatalogValidationException(HttpStatus.CONFLICT,
                                    "Estacao %s ja coberta pela equipe %s no turno %s"
                                            .formatted(estacaoId, outra.getId(), turno));
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
        return new TeamResponse(e.getId(), e.getNome(), e.getTurno(), List.copyOf(e.getEstacaoIds()), e.isAtiva());
    }
}
