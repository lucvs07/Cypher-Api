package com.dev4.cypher.service;

import com.dev4.cypher.catalog.CatalogCodes;
import com.dev4.cypher.catalog.CatalogValidationException;
import com.dev4.cypher.dto.CameraAdminResponse;
import com.dev4.cypher.dto.CameraConfigResponse;
import com.dev4.cypher.dto.CameraRequest;
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
    // Escrita de câmera
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<CameraAdminResponse> camera(String id) {
        // Responde também para câmera desativada: é preciso poder vê-la para
        // reativá-la. Quem filtra por ativa é a lista, não o item.
        return cameras.findById(id).map(this::toAdminResponse);
    }

    @Transactional
    public CameraAdminResponse createCamera(CameraRequest req) {
        if (cameras.existsById(req.id())) {
            throw new CatalogValidationException(HttpStatus.CONFLICT,
                    "Camera ja cadastrada: " + req.id());
        }
        exigirEstacao(req.estacaoId());
        exigirUrlLivre(req.streamUrl(), req.id());
        CatalogCodes.portaDe(req.streamUrl());

        Camera nova = new Camera(req.id(), req.nome(), req.estacaoId(),
                req.streamUrl(), req.fonte(), req.ativa() == null || req.ativa());
        return toAdminResponse(cameras.saveAndFlush(nova));
    }

    @Transactional
    public Optional<CameraAdminResponse> updateCamera(String id, CameraRequest req) {
        exigirMesmoCodigo(id, req.id(), "camera");
        return cameras.findById(id).map(camera -> {
            exigirEstacao(req.estacaoId());
            exigirUrlLivre(req.streamUrl(), id);
            CatalogCodes.portaDe(req.streamUrl());

            camera.setNome(req.nome());
            camera.setEstacaoId(req.estacaoId());
            camera.setStreamUrl(req.streamUrl());
            camera.setFonte(req.fonte());
            if (req.ativa() != null) {
                camera.setAtiva(req.ativa());
            }
            return toAdminResponse(cameras.saveAndFlush(camera));
        });
    }

    @Transactional
    public boolean deactivateCamera(String id) {
        return cameras.findById(id).map(camera -> {
            camera.setAtiva(false);
            cameras.saveAndFlush(camera);
            return true;
        }).orElse(false);
    }

    /**
     * Config do pipeline. Câmera desativada é conflito, não sucesso: o
     * pipeline não deve subir apontando para câmera tirada de operação.
     */
    @Transactional(readOnly = true)
    public Optional<CameraConfigResponse> cameraConfig(String id) {
        return cameras.findById(id).map(camera -> {
            if (!camera.isAtiva()) {
                throw new CatalogValidationException(HttpStatus.CONFLICT,
                        "Camera desativada: " + camera.getId());
            }
            return new CameraConfigResponse(
                    camera.getId(),
                    camera.getNome(),
                    camera.getEstacaoId(),
                    camera.getFonte(),
                    CatalogCodes.portaDe(camera.getStreamUrl()),
                    camera.isAtiva());
        });
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

    /**
     * Cadastro é estrito onde a ingestão é permissiva: um incidente de câmera
     * desconhecida é aceito (nunca perder dado), mas cadastrar câmera de
     * estação inexistente falha rápido, porque é ação humana deliberada.
     */
    private void exigirEstacao(String estacaoId) {
        if (!estacoes.existsById(estacaoId)) {
            throw new CatalogValidationException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Estacao inexistente: " + estacaoId);
        }
    }

    /**
     * Duas câmeras com o mesmo streamUrl fariam dois pipelines disputar o
     * mesmo bind, e um deles morreria no boot.
     */
    private void exigirUrlLivre(String streamUrl, String idPropria) {
        cameras.findByStreamUrl(streamUrl)
                .filter(outra -> !outra.getId().equals(idPropria))
                .ifPresent(outra -> {
                    throw new CatalogValidationException(HttpStatus.CONFLICT,
                            "streamUrl ja usada pela camera " + outra.getId());
                });
    }

    private StationResponse toResponse(Estacao e) {
        return new StationResponse(e.getId(), e.getNome(), e.getSetor(), e.isAtiva(),
                cameras.findByEstacaoIdAndAtivaTrue(e.getId()).stream()
                        .map(this::toResponse).toList());
    }

    private CameraResponse toResponse(Camera c) {
        return new CameraResponse(c.getId(), c.getNome(), c.getEstacaoId(), c.getStreamUrl());
    }

    private CameraAdminResponse toAdminResponse(Camera c) {
        return new CameraAdminResponse(c.getId(), c.getNome(), c.getEstacaoId(),
                c.getStreamUrl(), c.getFonte(), c.isAtiva());
    }
}
