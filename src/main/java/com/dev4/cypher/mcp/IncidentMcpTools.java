package com.dev4.cypher.mcp;

import com.dev4.cypher.dto.IncidentResponse;
import com.dev4.cypher.dto.RiskAnalysisResponse;
import com.dev4.cypher.llm.LlmAnalysisService;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.service.IncidentService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Tools MCP de leitura/análise de incidentes — delega inteiramente para IncidentService/LlmAnalysisService. */
@Component
public class IncidentMcpTools {

    private final IncidentService incidentes;
    private final LlmAnalysisService llm;

    public IncidentMcpTools(IncidentService incidentes, LlmAnalysisService llm) {
        this.incidentes = incidentes;
        this.llm = llm;
    }

    @McpTool(name = "list_incidents", description = "Lista incidentes da empresa, com filtros opcionais.")
    public Page<IncidentResponse> listIncidents(
            @McpToolParam(description = "Data/hora mínima (ISO-8601), ou null para sem filtro", required = false)
            OffsetDateTime since,
            @McpToolParam(description = "Código da câmera, ou null para todas", required = false)
            String cameraId,
            @McpToolParam(description = "Severidade mínima (0-4), ou null para sem filtro", required = false)
            Integer severityMin,
            @McpToolParam(description = "Status do incidente (ABERTO/FECHADO), ou null para todos", required = false)
            AlertaStatus status,
            @McpToolParam(description = "Página (0-based)", required = true) int page,
            @McpToolParam(description = "Tamanho da página", required = true) int size) {
        return incidentes.search(since, cameraId, severityMin, status, null, PageRequest.of(page, size));
    }

    @McpTool(name = "get_incident", description = "Busca um incidente pelo id.")
    public Optional<IncidentResponse> getIncident(
            @McpToolParam(description = "Id do incidente", required = true) Long id) {
        return incidentes.findById(id);
    }

    @McpTool(name = "get_incident_analysis",
            description = "Le a ultima analise de risco (via LLM) ja calculada para um incidente, sem acionar o LLM. Rapido.")
    public Optional<RiskAnalysisResponse> getIncidentAnalysis(
            @McpToolParam(description = "Id do incidente", required = true) Long id) {
        // LlmAnalysisService nao e escopado por tenant (achado de seguranca da
        // revisao final de branch) — a mesma guarda do IncidentController:
        // so prossegue se o incidente existir NO TENANT ATUAL.
        if (incidentes.findById(id).isEmpty()) {
            return Optional.empty();
        }
        return llm.ultimaAnalise(id);
    }

    @McpTool(name = "analyze_incident",
            description = "Aciona uma nova analise de risco via LLM para um incidente. "
                    + "OPERACAO LENTA: pode levar ate ~1 minuto (retries com backoff embutidos). "
                    + "Use get_incident_analysis se uma analise ja calculada for suficiente.")
    public RiskAnalysisResponse analyzeIncident(
            @McpToolParam(description = "Id do incidente", required = true) Long id) {
        // Mesma guarda: LlmAnalysisService.analisar nao e escopado por tenant.
        if (incidentes.findById(id).isEmpty()) {
            throw new NoSuchElementException("Incidente nao encontrado: " + id);
        }
        return llm.analisar(id);
    }
}
