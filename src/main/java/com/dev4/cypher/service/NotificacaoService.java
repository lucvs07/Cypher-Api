package com.dev4.cypher.service;

import com.dev4.cypher.dto.NotificacaoResponse;
import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.model.AlertaLeitura;
import com.dev4.cypher.repository.AlertaLeituraRepository;
import com.dev4.cypher.repository.AlertaRepository;
import com.dev4.cypher.security.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NotificacaoService {

    private static final int JANELA = 30;

    private final AlertaRepository alertas;
    private final AlertaLeituraRepository leituras;

    public NotificacaoService(AlertaRepository alertas, AlertaLeituraRepository leituras) {
        this.alertas = alertas;
        this.leituras = leituras;
    }

    /** Últimas 30 notificações da empresa, mais recente primeiro, cada uma com o flag `lida` do usuário atual. */
    public List<NotificacaoResponse> listar(String empresaId, String usuario) {
        List<Alerta> recentes = alertas.buscar(empresaId, null, null, null, null, null,
                PageRequest.of(0, JANELA, Sort.by("dataHora").descending())).getContent();

        Set<Long> lidos = recentes.isEmpty() ? Set.of()
                : leituras.findByEmpresaIdAndUsuarioAndAlertaIdIn(empresaId, usuario,
                        recentes.stream().map(Alerta::getId).toList())
                    .stream().map(AlertaLeitura::getAlertaId).collect(Collectors.toSet());

        return recentes.stream()
                .map(a -> new NotificacaoResponse(a.getId(), TenantContext.codigo(a.getCameraId()), a.getTipo(),
                        a.getSeveridade(), a.getNivel(), a.getDescricao(), a.getDataHora(), lidos.contains(a.getId())))
                .toList();
    }

    public long contarNaoLidas(String empresaId, String usuario) {
        return alertas.contarNaoLidas(empresaId, usuario);
    }

    /**
     * Cria a linha de leitura para cada id que pertence à empresa e ainda
     * não foi lido; ids de outra empresa ou já lidos são ignorados em
     * silêncio — idempotente por natureza (spec central-notificacoes §3/§5).
     * A constraint única em {@code (empresaId, usuario, alertaId)} é o
     * backstop contra a corrida de duas requisições concorrentes (ex.:
     * duplo clique, duas abas abertas) marcando o mesmo alerta como lido
     * entre o {@code existsBy} e o {@code save} abaixo — se ela disparar,
     * tratamos como o mesmo no-op do caso "já lido", em vez de deixar o
     * 500 vazar (não há {@code @RestControllerAdvice} genérico neste
     * projeto para traduzi-lo).
     */
    @Transactional
    public void marcarLidas(String empresaId, String usuario, List<Long> alertaIds) {
        for (Long alertaId : alertaIds) {
            boolean pertenceAEmpresa = alertas.findByIdAndEmpresaId(alertaId, empresaId).isPresent();
            if (!pertenceAEmpresa) {
                continue;
            }
            if (leituras.existsByEmpresaIdAndUsuarioAndAlertaId(empresaId, usuario, alertaId)) {
                continue;
            }
            AlertaLeitura leitura = new AlertaLeitura();
            leitura.setEmpresaId(empresaId);
            leitura.setUsuario(usuario);
            leitura.setAlertaId(alertaId);
            leitura.setLidoEm(OffsetDateTime.now());
            try {
                leituras.save(leitura);
            } catch (DataIntegrityViolationException jaLido) {
                // Outra requisição concorrente já gravou a leitura entre o
                // existsBy acima e este save — no-op, mesmo efeito de "já lido".
            }
        }
    }
}
