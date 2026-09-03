package com.dev4.cypher.controller;

import com.dev4.cypher.dto.IncidentCloseRequest;
import com.dev4.cypher.dto.IncidentRequest;
import com.dev4.cypher.dto.IncidentResponse;
import com.dev4.cypher.dto.RiskAnalysisResponse;
import com.dev4.cypher.dto.StatusRequest;
import com.dev4.cypher.llm.LlmAnalysisService;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.security.OperadorAuthentication;
import com.dev4.cypher.security.PipelineAuthentication;
import com.dev4.cypher.security.TenantContext;
import com.dev4.cypher.service.IncidentService;
import com.dev4.cypher.sse.IncidentSseService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService service;
    private final IncidentSseService sse;
    private final CameraRepository cameras;
    private final LlmAnalysisService llmAnalysisService;

    public IncidentController(IncidentService service, IncidentSseService sse, CameraRepository cameras,
                               LlmAnalysisService llmAnalysisService) {
        this.service = service;
        this.sse = sse;
        this.cameras = cameras;
        this.llmAnalysisService = llmAnalysisService;
    }

    /**
     * Fluxo de incidentes em tempo real; OPERADOR recebe um fluxo já filtrado
     * pela própria estação. {@link IncidentSseService} compara contra
     * {@code Camera.estacaoId} (prefixado) — diferente de
     * {@code estacaoIdDoOperadorAtual()}, que devolve o código puro para
     * {@code IncidentService.search} (Step 5); aqui é reprefixado antes de
     * repassar.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        String estacaoId = estacaoIdDoOperadorAtual();
        return sse.subscribe(estacaoId == null ? null : TenantContext.key(estacaoId));
    }

    /**
     * Ingestão vinda do pipeline. Idempotente por {@code incidentKey}: um retry
     * após timeout devolve 200 com o incidente existente, em vez de duplicar.
     */
    @PostMapping
    public ResponseEntity<IncidentResponse> ingest(@Valid @RequestBody IncidentRequest req) {
        if (chaveDeOutraCamera(TenantContext.key(req.cameraId()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        IncidentService.IngestResult resultado = service.ingest(req);
        HttpStatus status = resultado.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(resultado.incident());
    }

    /** Fechamento do episódio e/ou chegada tardia do snapshot. */
    @PatchMapping("/{incidentKey}")
    public ResponseEntity<IncidentResponse> close(@PathVariable String incidentKey,
                                                  @Valid @RequestBody IncidentCloseRequest req) {
        Optional<IncidentResponse> atual = service.findByIncidentKey(incidentKey);
        if (atual.isPresent() && chaveDeOutraCamera(TenantContext.key(atual.get().cameraId()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return service.close(incidentKey, req)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Histórico com filtros opcionais, mais recentes primeiro.
     *
     * <p>{@code since} é o que o front usa para cobrir a lacuna depois de o SSE
     * cair e reconectar.
     */
    @GetMapping
    public Page<IncidentResponse> list(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(required = false) String camera,
            @RequestParam(required = false) Integer severityMin,
            @RequestParam(required = false) AlertaStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "dataHora"));
        return service.search(since, camera, severityMin, status, estacaoIdDoOperadorAtual(), pageable);
    }

    /** Item individual, para a tela de detalhe sobreviver a refresh/link direto. */
    @GetMapping("/{id}")
    public ResponseEntity<IncidentResponse> byId(@PathVariable Long id) {
        return service.findById(id).map(incident -> foraDoEscopoDoOperador(incident.cameraId())
                        ? ResponseEntity.status(HttpStatus.FORBIDDEN).<IncidentResponse>build()
                        : ResponseEntity.ok(incident))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<IncidentResponse> changeStatus(@PathVariable Long id,
                                                         @Valid @RequestBody StatusRequest req) {
        Optional<IncidentResponse> atual = service.findById(id);
        if (atual.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (foraDoEscopoDoOperador(atual.get().cameraId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return service.changeStatus(id, req.status())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Aciona a análise de risco por LLM multimodal sobre o snapshot do incidente (spec §2.5). */
    @PostMapping("/{id}/analyze")
    public ResponseEntity<RiskAnalysisResponse> analyze(@PathVariable Long id) {
        if (service.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(llmAnalysisService.analisar(id));
    }

    /** Ultima analise ja feita, sem chamar o provedor de novo — 404 se nunca foi analisado. */
    @GetMapping("/{id}/analyze")
    public ResponseEntity<RiskAnalysisResponse> ultimaAnalise(@PathVariable Long id) {
        if (service.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return llmAnalysisService.ultimaAnalise(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** `null` para GESTOR (sem filtro) ou sessão anônima; a própria estacaoId (código puro) para OPERADOR. */
    private String estacaoIdDoOperadorAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof OperadorAuthentication operador
                ? TenantContext.codigo(operador.getEstacaoId()) : null;
    }

    /**
     * Câmera desconhecida conta como fora do escopo — um OPERADOR nunca vê o
     * que não se sabe de qual estação é (mesma política do broadcast SSE).
     */
    private boolean foraDoEscopoDoOperador(String cameraId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof OperadorAuthentication operador)) {
            return false;
        }
        return cameras.findById(TenantContext.key(operador.getEmpresaId(), cameraId))
                .map(c -> !c.getEstacaoId().equals(operador.getEstacaoId()))
                .orElse(true);
    }

    /**
     * Trava a chave de ingestão por câmera (spec E4 §2.3/achado de segurança
     * da revisão final): uma chave X-Cypher-Key válida só pode agir sobre
     * incidentes DA PRÓPRIA câmera — nunca de qualquer outra. Só se aplica
     * quando o request está autenticado via {@link PipelineAuthentication};
     * sessão GESTOR/OPERADOR (ou anônimo, onde a rota é permitAll) não é
     * afetada por esta checagem. {@code cameraIdDoAlvo} já chega prefixado
     * pelo tenant (chamadores usam {@link TenantContext#key}).
     */
    private boolean chaveDeOutraCamera(String cameraIdDoAlvo) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof PipelineAuthentication pipeline
                && !pipeline.getCameraId().equals(cameraIdDoAlvo);
    }
}
