package com.dev4.cypher.service;

import com.dev4.cypher.catalog.CatalogValidationException;
import com.dev4.cypher.dto.CameraResponse;
import com.dev4.cypher.dto.StationRequest;
import com.dev4.cypher.dto.StationResponse;
import com.dev4.cypher.model.Camera;
import com.dev4.cypher.model.Estacao;
import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.repository.EstacaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Catálogo de lugar: estações e as câmeras que as cobrem.
 *
 * <p>As listas devolvem apenas registros ativos, o que é o que faz uma estação
 * desativada sair do heatmap — o {@code SummaryService} monta as células a
 * partir daqui.
 */
@Service
public class CatalogService {

    private final EstacaoRepository estacoes;
    private final CameraRepository cameras;

    public CatalogService(EstacaoRepository estacoes, CameraRepository cameras) {
        this.estacoes = estacoes;
        this.cameras = cameras;
    }

    // ------------------------------------------------------------------
    // Leitura
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CameraResponse> cameras() {
        return cameras.findByAtivaTrue().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<StationResponse> stations() {
        return stations(false);
    }

    @Transactional(readOnly = true)
    public List<StationResponse> stations(boolean incluirInativas) {
        List<Estacao> base = incluirInativas ? estacoes.findAll() : estacoes.findByAtivaTrue();
        return base.stream().map(this::toResponse).toList();
    }

    // ------------------------------------------------------------------
    // Escrita de estação
    // ------------------------------------------------------------------

    @Transactional
    public StationResponse createStation(StationRequest req) {
        if (estacoes.existsById(req.id())) {
            throw new CatalogValidationException(HttpStatus.CONFLICT,
                    "Estacao ja cadastrada: " + req.id());
        }
        Estacao nova = new Estacao(req.id(), req.nome(), req.setor(),
                req.ativa() == null || req.ativa());
        return toResponse(estacoes.saveAndFlush(nova));
    }

    @Transactional
    public Optional<StationResponse> updateStation(String id, StationRequest req) {
        exigirMesmoCodigo(id, req.id(), "estacao");
        return estacoes.findById(id).map(estacao -> {
            estacao.setNome(req.nome());
            estacao.setSetor(req.setor());
            // ativa nulo significa "nao mexer": evita reativar por acidente
            // num PUT que so queria corrigir o nome.
            if (req.ativa() != null) {
                estacao.setAtiva(req.ativa());
            }
            return toResponse(estacoes.saveAndFlush(estacao));
        });
    }

    @Transactional
    public boolean deactivateStation(String id) {
        return estacoes.findById(id).map(estacao -> {
            estacao.setAtiva(false);
            estacoes.saveAndFlush(estacao);
            return true;
        }).orElse(false);
    }

    // ------------------------------------------------------------------
    // Interno
    // ------------------------------------------------------------------

    /**
     * O código é imutável: está embutido no incidentKey de todo incidente já
     * gravado, então renomear reescreveria histórico.
     */
    void exigirMesmoCodigo(String naUrl, String noCorpo, String entidade) {
        if (noCorpo != null && !noCorpo.equals(naUrl)) {
            throw new CatalogValidationException(HttpStatus.CONFLICT,
                    "O codigo de %s e imutavel: %s nao pode virar %s"
                            .formatted(entidade, naUrl, noCorpo));
        }
    }

    private StationResponse toResponse(Estacao e) {
        return new StationResponse(e.getId(), e.getNome(), e.getSetor(), e.isAtiva(),
                cameras.findByEstacaoIdAndAtivaTrue(e.getId()).stream()
                        .map(this::toResponse).toList());
    }

    private CameraResponse toResponse(Camera c) {
        return new CameraResponse(c.getId(), c.getNome(), c.getEstacaoId(), c.getStreamUrl());
    }
}
