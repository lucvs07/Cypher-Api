package com.dev4.cypher.service;

import com.dev4.cypher.dto.NotificacaoResponse;
import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.model.AlertaLeitura;
import com.dev4.cypher.repository.AlertaLeituraRepository;
import com.dev4.cypher.repository.AlertaRepository;
import com.dev4.cypher.security.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

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
}
