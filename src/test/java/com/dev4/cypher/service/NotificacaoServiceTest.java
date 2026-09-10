package com.dev4.cypher.service;

import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.repository.AlertaLeituraRepository;
import com.dev4.cypher.repository.AlertaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code marcarLidas} não deve deixar vazar um 500 quando a constraint única
 * de {@code AlertaLeitura} dispara — cenário de corrida entre o
 * {@code existsBy} e o {@code save} (duas requisições concorrentes marcando
 * o mesmo alerta como lido ao mesmo tempo). Simulado aqui com mocks porque
 * reproduzir a corrida de verdade via MockMvc/H2 exigiria threads
 * concorrentes disputando a mesma linha, o que seria não-determinístico;
 * mockar {@code save} para lançar a mesma exceção que a constraint jogaria
 * cobre o mesmo caminho de código de forma determinística (achado da
 * revisão da Tarefa 3).
 */
class NotificacaoServiceTest {

    @Test
    void marcarLidasNaoPropagaViolacaoDeConstraintQuandoOutraRequisicaoJaGravouEntreOExistsEOSave() {
        AlertaRepository alertas = mock(AlertaRepository.class);
        AlertaLeituraRepository leituras = mock(AlertaLeituraRepository.class);
        NotificacaoService service = new NotificacaoService(alertas, leituras);

        when(alertas.findByIdAndEmpresaId(anyLong(), anyString())).thenReturn(Optional.of(new Alerta()));
        when(leituras.existsByEmpresaIdAndUsuarioAndAlertaId(anyString(), anyString(), anyLong())).thenReturn(false);
        when(leituras.save(any())).thenThrow(new DataIntegrityViolationException("unique constraint"));

        service.marcarLidas("acme-test", "admin@acme-test", List.of(42L));

        verify(leituras).save(any());
    }
}
