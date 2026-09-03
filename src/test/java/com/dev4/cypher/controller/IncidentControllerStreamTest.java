package com.dev4.cypher.controller;

import com.dev4.cypher.llm.LlmAnalysisService;
import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.security.GestorAuthentication;
import com.dev4.cypher.security.OperadorAuthentication;
import com.dev4.cypher.service.IncidentService;
import com.dev4.cypher.sse.IncidentSseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@code stream()} sempre passa o {@code empresaId} da sessão atual (Tarefa 6)
 * — a inscrição no SSE nunca é irrestrita entre tenants, mesmo para GESTOR.
 * A {@code estacaoId} do OPERADOR chega em código puro, a mesma forma que
 * {@code IncidentSseService.broadcast} agora compara (Tarefa 6, achado da
 * revisão da Tarefa 5 sobre reprefixação).
 */
class IncidentControllerStreamTest {

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void streamDeOperadorSeInscreveNaPropriaEmpresaEEstacao() {
        IncidentSseService sse = mock(IncidentSseService.class);
        CameraRepository cameras = mock(CameraRepository.class);
        IncidentService service = mock(IncidentService.class);
        LlmAnalysisService llm = mock(LlmAnalysisService.class);
        IncidentController controller = new IncidentController(service, sse, cameras, llm);

        SecurityContextHolder.getContext().setAuthentication(
                new OperadorAuthentication("acme-test:EST-91", "acme-test"));

        controller.stream();

        verify(sse).subscribe("acme-test", "EST-91");
    }

    @Test
    void streamDeGestorSeInscreveNaPropriaEmpresaSemFiltroDeEstacao() {
        IncidentSseService sse = mock(IncidentSseService.class);
        CameraRepository cameras = mock(CameraRepository.class);
        IncidentService service = mock(IncidentService.class);
        LlmAnalysisService llm = mock(LlmAnalysisService.class);
        IncidentController controller = new IncidentController(service, sse, cameras, llm);

        SecurityContextHolder.getContext().setAuthentication(
                new GestorAuthentication("gestor@acme-test", "acme-test"));

        controller.stream();

        verify(sse).subscribe("acme-test", null);
    }
}
