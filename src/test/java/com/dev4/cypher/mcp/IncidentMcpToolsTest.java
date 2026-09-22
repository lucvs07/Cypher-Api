package com.dev4.cypher.mcp;

import com.dev4.cypher.dto.IncidentResponse;
import com.dev4.cypher.dto.RiskAnalysisResponse;
import com.dev4.cypher.llm.LlmAnalysisService;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class IncidentMcpToolsTest {

    private final IncidentService incidentes = Mockito.mock(IncidentService.class);
    private final LlmAnalysisService llm = Mockito.mock(LlmAnalysisService.class);
    private final IncidentMcpTools tools = new IncidentMcpTools(incidentes, llm);

    @Test
    void listIncidentsDelegaParaSearchComPaginacaoPadrao() {
        Page<IncidentResponse> pagina = new PageImpl<>(List.of());
        when(incidentes.search(isNull(), eq("CAM-01"), eq(2), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(pagina);

        Page<IncidentResponse> resultado = tools.listIncidents(null, "CAM-01", 2, null, 0, 20);

        assertThat(resultado).isSameAs(pagina);
    }

    @Test
    void getIncidentDelegaParaFindById() {
        IncidentResponse resposta = Mockito.mock(IncidentResponse.class);
        when(incidentes.findById(10L)).thenReturn(Optional.of(resposta));

        Optional<IncidentResponse> resultado = tools.getIncident(10L);

        assertThat(resultado).contains(resposta);
    }

    @Test
    void getIncidentAnalysisDelegaParaUltimaAnaliseSemChamarOLlm() {
        RiskAnalysisResponse resposta = Mockito.mock(RiskAnalysisResponse.class);
        when(llm.ultimaAnalise(10L)).thenReturn(Optional.of(resposta));

        Optional<RiskAnalysisResponse> resultado = tools.getIncidentAnalysis(10L);

        assertThat(resultado).contains(resposta);
        Mockito.verify(llm, Mockito.never()).analisar(Mockito.anyLong());
    }

    @Test
    void analyzeIncidentDelegaParaAnalisar() {
        RiskAnalysisResponse resposta = Mockito.mock(RiskAnalysisResponse.class);
        when(llm.analisar(10L)).thenReturn(resposta);

        RiskAnalysisResponse resultado = tools.analyzeIncident(10L);

        assertThat(resultado).isSameAs(resposta);
    }
}
