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
        List<Alerta> recentes = buscarJanela(empresaId);
        Set<Long> lidos = idsLidos(empresaId, usuario, recentes);

        return recentes.stream()
                .map(a -> new NotificacaoResponse(a.getId(), TenantContext.codigo(a.getCameraId()), a.getTipo(),
                        a.getSeveridade(), a.getNivel(), a.getDescricao(), a.getDataHora(), lidos.contains(a.getId())))
                .toList();
    }

    /**
     * Não lidas dentro da mesma janela das últimas 30 notificações que
     * {@link #listar} expõe — deliberadamente NÃO é uma contagem de todo o
     * histórico da empresa (decisão de escopo pós-entrega: ver
     * badge-window-fix-report.md). Isso garante que abrir o painel e
     * marcar como lidos os ids ali listados sempre consegue zerar o
     * badge, mesmo que existam alertas não lidos mais antigos fora da
     * janela.
     */
    public long contarNaoLidas(String empresaId, String usuario) {
        List<Alerta> recentes = buscarJanela(empresaId);
        Set<Long> lidos = idsLidos(empresaId, usuario, recentes);
        return recentes.stream().filter(a -> !lidos.contains(a.getId())).count();
    }

    private List<Alerta> buscarJanela(String empresaId) {
        return alertas.buscar(empresaId, null, null, null, null, null,
                PageRequest.of(0, JANELA, Sort.by("dataHora").descending())).getContent();
    }

    private Set<Long> idsLidos(String empresaId, String usuario, List<Alerta> janela) {
        return janela.isEmpty() ? Set.of()
                : leituras.findByEmpresaIdAndUsuarioAndAlertaIdIn(empresaId, usuario,
                        janela.stream().map(Alerta::getId).toList())
                    .stream().map(AlertaLeitura::getAlertaId).collect(Collectors.toSet());
    }

    /**
     * Cria a linha de leitura para cada id que pertence à empresa e ainda
     * não foi lido; ids de outra empresa ou já lidos são ignorados em
     * silêncio — idempotente por natureza (spec central-notificacoes §3/§5).
     *
     * <p>Deliberadamente SEM {@code @Transactional} neste método: cada
     * consulta (batch de pertencimento, batch de já-lidos) e o
     * {@code saveAll} final rodam em sua própria transação curta (a
     * transação implícita do próprio método do Spring Data), em vez de
     * todos compartilharem uma única transação do método de serviço. Isso
     * importa porque {@code AlertaLeitura} usa {@code GenerationType.IDENTITY}
     * — o {@code INSERT} é disparado na hora do {@code save}, então uma
     * violação da constraint única já surge ali; só que, com o método
     * inteiro sob {@code @Transactional}, a tradução de exceção do
     * Hibernate marca essa transação ENVOLVENTE como rollback-only, e no
     * commit final o {@code JpaTransactionManager} lança
     * {@code UnexpectedRollbackException} — outro 500, e descarta também
     * toda linha de {@code AlertaLeitura} já gravada com sucesso mais cedo
     * no mesmo laço/requisição. Sem a transação envolvente, um
     * {@code DataIntegrityViolationException} vindo do {@code saveAll}
     * fica contido à transação do próprio {@code saveAll}, e o catch abaixo
     * (mantido como defesa em profundidade contra a corrida entre a
     * consulta de "já lidos" e este save) realmente reduz o efeito a um
     * no-op desta chamada — sem UnexpectedRollbackException e sem afetar
     * chamadas/linhas anteriores.
     */
    public void marcarLidas(String empresaId, String usuario, List<Long> alertaIds) {
        if (alertaIds.isEmpty()) {
            return;
        }

        List<Long> pertencentesAEmpresa = alertas.findAllByIdInAndEmpresaId(alertaIds, empresaId)
                .stream().map(Alerta::getId).toList();
        if (pertencentesAEmpresa.isEmpty()) {
            return;
        }

        Set<Long> jaLidos = leituras.findByEmpresaIdAndUsuarioAndAlertaIdIn(empresaId, usuario, pertencentesAEmpresa)
                .stream().map(AlertaLeitura::getAlertaId).collect(Collectors.toSet());

        List<AlertaLeitura> novasLeituras = pertencentesAEmpresa.stream()
                .filter(id -> !jaLidos.contains(id))
                .map(id -> {
                    AlertaLeitura leitura = new AlertaLeitura();
                    leitura.setEmpresaId(empresaId);
                    leitura.setUsuario(usuario);
                    leitura.setAlertaId(id);
                    leitura.setLidoEm(OffsetDateTime.now());
                    return leitura;
                })
                .toList();
        if (novasLeituras.isEmpty()) {
            return;
        }

        try {
            leituras.saveAll(novasLeituras);
        } catch (DataIntegrityViolationException jaLido) {
            // Outra requisição concorrente gravou uma destas leituras entre
            // o findByEmpresaIdAndUsuarioAndAlertaIdIn acima e este
            // saveAll — no-op, mesmo efeito de "já lido" (defesa em
            // profundidade; ver Javadoc do método sobre por que isto agora
            // realmente funciona em produção).
        }
    }
}
