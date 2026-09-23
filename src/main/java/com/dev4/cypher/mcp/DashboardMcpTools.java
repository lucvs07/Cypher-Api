package com.dev4.cypher.mcp;

import com.dev4.cypher.dto.PlacarEquipeResponse;
import com.dev4.cypher.dto.SummaryResponse;
import com.dev4.cypher.service.PlacarService;
import com.dev4.cypher.service.SummaryService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/** Tools MCP de dashboard/gamificação — delega para SummaryService/PlacarService. */
@Component
public class DashboardMcpTools {

    private final SummaryService summary;
    private final PlacarService placar;

    public DashboardMcpTools(SummaryService summary, PlacarService placar) {
        this.summary = summary;
        this.placar = placar;
    }

    @McpTool(name = "get_summary", description = "Resumo/heatmap do dashboard: contagens por estação, turno, setor e EPI.")
    public SummaryResponse getSummary(
            @McpToolParam(description = "Data/hora inicial (ISO-8601), ou null para padrão", required = false)
            OffsetDateTime since,
            @McpToolParam(description = "Data/hora final (ISO-8601), ou null para padrão", required = false)
            OffsetDateTime until,
            @McpToolParam(description = "Turno (A/B/N), ou null para todos", required = false) String turno,
            @McpToolParam(description = "Nome do setor, ou null para todos", required = false) String setor,
            @McpToolParam(description = "EPI ausente (HELMET/GOGGLES/GLOVE/VEST/BOOTS/MASK), ou null para todos", required = false)
            String epi,
            @McpToolParam(description = "Incluir distribuição temporal detalhada", required = true)
            boolean detalhado) {
        return summary.build(since, until, turno, setor, epi, detalhado);
    }

    @McpTool(name = "get_placar", description = "Placar de gamificação por equipe (conformidade, cobertura, sequência, ranking).")
    public List<PlacarEquipeResponse> getPlacar(
            @McpToolParam(description = "Data/hora inicial (ISO-8601), ou null para os últimos 7 dias", required = false)
            OffsetDateTime since,
            @McpToolParam(description = "Data/hora final (ISO-8601), ou null para agora", required = false)
            OffsetDateTime until) {
        OffsetDateTime[] janela = PlacarService.janelaPadrao(since, until);
        return placar.placar(janela[0], janela[1]);
    }
}
