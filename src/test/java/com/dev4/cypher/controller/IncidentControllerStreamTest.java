package com.dev4.cypher.controller;

import com.dev4.cypher.llm.LlmAnalysisService;
import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.security.OperadorAuthentication;
import com.dev4.cypher.service.IncidentService;
import com.dev4.cypher.sse.IncidentSseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Achado da revisão da Tarefa 5: {@code estacaoIdDoOperadorAtual()} passou a
 * devolver o código puro (para {@code IncidentService.search}, Step 5 do
 * plano) — mas {@code IncidentSseService.broadcast} compara contra
 * {@code Camera.estacaoId}, que é PREFIXADO. Sem reprefixar em
 * {@code stream()}, o OPERADOR se inscrevia com o código puro e nunca
 * recebia broadcast algum (o filtro nunca batia).
 */
class IncidentControllerStreamTest {

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void streamReprefixaEstacaoIdDoOperadorAntesDeInscrever() {
        IncidentSseService sse = mock(IncidentSseService.class);
        CameraRepository cameras = mock(CameraRepository.class);
        IncidentService service = mock(IncidentService.class);
        LlmAnalysisService llm = mock(LlmAnalysisService.class);
        IncidentController controller = new IncidentController(service, sse, cameras, llm);

        SecurityContextHolder.getContext().setAuthentication(
                new OperadorAuthentication("acme-test:EST-91", "acme-test"));

        controller.stream();

        verify(sse).subscribe("acme-test:EST-91");
    }

    @Test
    void streamDeGestorSeInscreveSemFiltro() {
        IncidentSseService sse = mock(IncidentSseService.class);
        CameraRepository cameras = mock(CameraRepository.class);
        IncidentService service = mock(IncidentService.class);
        LlmAnalysisService llm = mock(LlmAnalysisService.class);
        IncidentController controller = new IncidentController(service, sse, cameras, llm);

        controller.stream();

        verify(sse).subscribe(null);
    }
}
