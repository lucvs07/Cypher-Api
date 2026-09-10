package com.dev4.cypher.service;

import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.repository.AlertaLeituraRepository;
import com.dev4.cypher.repository.AlertaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code marcarLidas} não deve deixar vazar um 500 quando a constraint única
 * de {@code AlertaLeitura} dispara — cenário de corrida entre a consulta de
 * "já lidos" e o {@code saveAll} (duas requisições concorrentes marcando o
 * mesmo alerta como lido ao mesmo tempo). Simulado aqui com mocks porque é
 * determinístico e cobre o caminho de código do catch; a prova de que isto
 * também funciona com transações reais (sem {@code UnexpectedRollbackException}
 * do antigo {@code @Transactional} do método) está em
 * {@code NotificacaoMarcarLidasConcorrenciaApiTest}, que usa threads reais
 * contra o H2 (achado da revisão final da Tarefa 3).
 */
class NotificacaoServiceTest {

    @Test
    void marcarLidasNaoPropagaViolacaoDeConstraintQuandoOutraRequisicaoJaGravouEntreAConsultaEOSaveAll() {
        AlertaRepository alertas = mock(AlertaRepository.class);
        AlertaLeituraRepository leituras = mock(AlertaLeituraRepository.class);
        NotificacaoService service = new NotificacaoService(alertas, leituras);

        Alerta alerta = new Alerta();
        alerta.setId(42L);
        when(alertas.findAllByIdInAndEmpresaId(anyList(), anyString())).thenReturn(List.of(alerta));
        when(leituras.findByEmpresaIdAndUsuarioAndAlertaIdIn(anyString(), anyString(), anyList()))
                .thenReturn(List.of());
        when(leituras.saveAll(anyList())).thenThrow(new DataIntegrityViolationException("unique constraint"));

        service.marcarLidas("acme-test", "admin@acme-test", List.of(42L));

        verify(leituras).saveAll(anyList());
    }
}
