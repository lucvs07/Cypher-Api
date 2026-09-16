package com.dev4.cypher.service;

import com.dev4.cypher.catalog.CatalogCodes;
import com.dev4.cypher.catalog.CatalogValidationException;
import com.dev4.cypher.dto.CameraAdminResponse;
import com.dev4.cypher.dto.CameraApiKeyResponse;
import com.dev4.cypher.dto.CameraConfigResponse;
import com.dev4.cypher.dto.CameraRequest;
import com.dev4.cypher.dto.CameraResponse;
import com.dev4.cypher.dto.EstacaoAdminResponse;
import com.dev4.cypher.dto.StationRequest;
import com.dev4.cypher.dto.StationResponse;
import com.dev4.cypher.model.Camera;
import com.dev4.cypher.model.Estacao;
import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.repository.EstacaoRepository;
import com.dev4.cypher.security.TenantContext;
import com.dev4.cypher.security.TokenGenerator;
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
        return cameras(false);
    }

    @Transactional(readOnly = true)
    public List<CameraResponse> cameras(boolean incluirInativas) {
        String empresaId = TenantContext.currentEmpresaId();
        List<Camera> base = incluirInativas
                ? cameras.findByEmpresaId(empresaId) : cameras.findByEmpresaIdAndAtivaTrue(empresaId);
        return base.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<StationResponse> stations() {
        return stations(false);
    }

    @Transactional(readOnly = true)
    public List<StationResponse> stations(boolean incluirInativas) {
        String empresaId = TenantContext.currentEmpresaId();
        List<Estacao> base = incluirInativas
                ? estacoes.findByEmpresaId(empresaId) : estacoes.findByEmpresaIdAndAtivaTrue(empresaId);
        return base.stream().map(this::toResponse).toList();
    }

    // ------------------------------------------------------------------
    // Escrita de estação
    // ------------------------------------------------------------------

    @Transactional
    public EstacaoAdminResponse createStation(StationRequest req) {
        String id = TenantContext.key(req.id());
        if (estacoes.existsById(id)) {
            throw new CatalogValidationException(HttpStatus.CONFLICT,
                    "Estacao ja cadastrada: " + req.id());
        }
        Estacao nova = new Estacao(id, req.nome(), req.setor(),
                req.ativa() == null || req.ativa(), TokenGenerator.novo("est-tok"),
                TenantContext.currentEmpresaId());
        return toAdminResponse(estacoes.saveAndFlush(nova));
    }

    @Transactional
    public Optional<EstacaoAdminResponse> updateStation(String id, StationRequest req) {
        exigirMesmoCodigo(id, req.id(), "estacao");
        return estacoes.findById(TenantContext.key(id)).map(estacao -> {
            estacao.setNome(req.nome());
            estacao.setSetor(req.setor());
            // ativa nulo significa "nao mexer": evita reativar por acidente
            // num PUT que so queria corrigir o nome.
            if (req.ativa() != null) {
                estacao.setAtiva(req.ativa());
            }
            return toAdminResponse(estacoes.saveAndFlush(estacao));
        });
    }

    /** Rotaciona o token sem recriar a estação (spec E4 §2.4) — GESTOR-only. */
    @Transactional
    public Optional<EstacaoAdminResponse> regenerateToken(String id) {
        return estacoes.findById(TenantContext.key(id)).map(estacao -> {
            estacao.setToken(TokenGenerator.novo("est-tok"));
            return toAdminResponse(estacoes.saveAndFlush(estacao));
        });
    }

    @Transactional
    public boolean deactivateStation(String id) {
        return estacoes.findById(TenantContext.key(id)).map(estacao -> {
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
        return cameras.findById(TenantContext.key(id)).map(this::toAdminResponse);
    }

    @Transactional
    public CameraAdminResponse createCamera(CameraRequest req) {
        String id = TenantContext.key(req.id());
        if (cameras.existsById(id)) {
            throw new CatalogValidationException(HttpStatus.CONFLICT,
                    "Camera ja cadastrada: " + req.id());
        }
        String estacaoId = TenantContext.key(req.estacaoId());
        exigirEstacao(estacaoId);
        exigirUrlLivre(req.streamUrl(), id);
        CatalogCodes.portaDe(req.streamUrl());
        int rotacao = validarRotacao(req.rotacao());
        int angulo = req.anguloCameraGraus() == null ? 0 : req.anguloCameraGraus();

        Camera nova = new Camera(id, req.nome(), estacaoId,
                req.streamUrl(), req.fonte(), req.ativa() == null || req.ativa(),
                TokenGenerator.novo("cam-key"), TenantContext.currentEmpresaId(),
                rotacao, angulo);
        nova.setFallenAspectRatio(req.fallenAspectRatio());
        nova.setFallenVerticalExtent(req.fallenVerticalExtent());
        nova.setTrunkRotationMax(req.trunkRotationMax());
        nova.setFallRequireCorroboration(req.fallRequireCorroboration());
        return toAdminResponse(cameras.saveAndFlush(nova));
    }

    @Transactional
    public Optional<CameraAdminResponse> updateCamera(String id, CameraRequest req) {
        exigirMesmoCodigo(id, req.id(), "camera");
        String chave = TenantContext.key(id);
        return cameras.findById(chave).map(camera -> {
            String estacaoId = TenantContext.key(req.estacaoId());
            exigirEstacao(estacaoId);
            exigirUrlLivre(req.streamUrl(), chave);
            CatalogCodes.portaDe(req.streamUrl());

            camera.setNome(req.nome());
            camera.setEstacaoId(estacaoId);
            camera.setStreamUrl(req.streamUrl());
            camera.setFonte(req.fonte());
            if (req.ativa() != null) {
                camera.setAtiva(req.ativa());
            }
            if (req.rotacao() != null) {
                camera.setRotacao(validarRotacao(req.rotacao()));
            }
            if (req.anguloCameraGraus() != null) {
                camera.setAnguloCameraGraus(req.anguloCameraGraus());
            }
            // Overrides de tuning de queda: sempre sobrescreve (inclusive pra
            // null), porque null AQUI e um estado valido e intencional ("volta
            // a usar o default do YAML"), diferente do "nao mexer" de ativa/
            // rotacao/anguloCameraGraus.
            camera.setFallenAspectRatio(req.fallenAspectRatio());
            camera.setFallenVerticalExtent(req.fallenVerticalExtent());
            camera.setTrunkRotationMax(req.trunkRotationMax());
            camera.setFallRequireCorroboration(req.fallRequireCorroboration());
            return toAdminResponse(cameras.saveAndFlush(camera));
        });
    }

    /**
     * Rotaciona a apiKey sem recriar a câmera — mesmo padrão de {@code
     * regenerateToken} para estação (spec E4 §2.3/§2.4, gap de
     * provisionamento fechado pós-E4). GESTOR-only.
     */
    @Transactional
    public Optional<CameraApiKeyResponse> regenerateApiKey(String id) {
        return cameras.findById(TenantContext.key(id)).map(camera -> {
            camera.setApiKey(TokenGenerator.novo("cam-key"));
            return toApiKeyResponse(cameras.saveAndFlush(camera));
        });
    }

    @Transactional
    public boolean deactivateCamera(String id) {
        return cameras.findById(TenantContext.key(id)).map(camera -> {
            camera.setAtiva(false);
            cameras.saveAndFlush(camera);
            return true;
        }).orElse(false);
    }

    /**
     * Config do pipeline. Câmera desativada é conflito, não sucesso: o
     * pipeline não deve subir apontando para câmera tirada de operação. O
     * mesmo vale se a estação da câmera foi desativada: a câmera continua
     * marcada como ativa, mas o posto que ela cobre saiu de operação.
     */
    @Transactional(readOnly = true)
    public Optional<CameraConfigResponse> cameraConfig(String id) {
        return cameras.findById(TenantContext.key(id)).map(camera -> {
            if (!camera.isAtiva()) {
                throw new CatalogValidationException(HttpStatus.CONFLICT,
                        "Camera desativada: " + TenantContext.codigo(camera.getId()));
            }
            estacoes.findById(camera.getEstacaoId())
                    .filter(estacao -> !estacao.isAtiva())
                    .ifPresent(estacao -> {
                        throw new CatalogValidationException(HttpStatus.CONFLICT,
                                "Estacao da camera esta desativada: " + TenantContext.codigo(estacao.getId()));
                    });
            return new CameraConfigResponse(
                    TenantContext.codigo(camera.getId()),
                    camera.getNome(),
                    TenantContext.codigo(camera.getEstacaoId()),
                    camera.getFonte(),
                    CatalogCodes.portaDe(camera.getStreamUrl()),
                    camera.isAtiva(),
                    camera.getRotacao(),
                    camera.getAnguloCameraGraus(),
                    camera.getFallenAspectRatio(),
                    camera.getFallenVerticalExtent(),
                    camera.getTrunkRotationMax(),
                    camera.getFallRequireCorroboration());
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
    private static final java.util.Set<Integer> ROTACOES_VALIDAS = java.util.Set.of(0, 90, 180, 270);

    private int validarRotacao(Integer rotacao) {
        int valor = rotacao == null ? 0 : rotacao;
        if (!ROTACOES_VALIDAS.contains(valor)) {
            throw new CatalogValidationException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "rotacao deve ser 0, 90, 180 ou 270: " + valor);
        }
        return valor;
    }

    private void exigirEstacao(String estacaoIdPrefixado) {
        if (!estacoes.existsById(estacaoIdPrefixado)) {
            throw new CatalogValidationException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Estacao inexistente: " + TenantContext.codigo(estacaoIdPrefixado));
        }
    }

    /**
     * Duas câmeras com o mesmo streamUrl fariam dois pipelines disputar o
     * mesmo bind, e um deles morreria no boot.
     */
    private void exigirUrlLivre(String streamUrl, String idPropriaPrefixada) {
        cameras.findByEmpresaIdAndStreamUrl(TenantContext.currentEmpresaId(), streamUrl)
                .filter(outra -> !outra.getId().equals(idPropriaPrefixada))
                .ifPresent(outra -> {
                    throw new CatalogValidationException(HttpStatus.CONFLICT,
                            "streamUrl ja usada pela camera " + TenantContext.codigo(outra.getId()));
                });
    }

    private StationResponse toResponse(Estacao e) {
        return new StationResponse(TenantContext.codigo(e.getId()), e.getNome(), e.getSetor(), e.isAtiva(),
                cameras.findByEstacaoIdAndAtivaTrue(e.getId()).stream()
                        .map(this::toResponse).toList());
    }

    private EstacaoAdminResponse toAdminResponse(Estacao e) {
        return new EstacaoAdminResponse(TenantContext.codigo(e.getId()), e.getNome(), e.getSetor(), e.isAtiva(), e.getToken());
    }

    private CameraResponse toResponse(Camera c) {
        return new CameraResponse(TenantContext.codigo(c.getId()), c.getNome(),
                TenantContext.codigo(c.getEstacaoId()), c.getStreamUrl());
    }

    private CameraAdminResponse toAdminResponse(Camera c) {
        return new CameraAdminResponse(TenantContext.codigo(c.getId()), c.getNome(),
                TenantContext.codigo(c.getEstacaoId()), c.getStreamUrl(), c.getFonte(), c.isAtiva(),
                c.getRotacao(), c.getAnguloCameraGraus());
    }

    private CameraApiKeyResponse toApiKeyResponse(Camera c) {
        return new CameraApiKeyResponse(TenantContext.codigo(c.getId()), c.getApiKey());
    }
}
