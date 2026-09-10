package com.dev4.cypher.controller;

import com.dev4.cypher.dto.ContagemNotificacoesResponse;
import com.dev4.cypher.dto.NotificacaoResponse;
import com.dev4.cypher.security.TenantContext;
import com.dev4.cypher.service.NotificacaoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notificacoes")
public class NotificacaoController {

    private final NotificacaoService service;

    public NotificacaoController(NotificacaoService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificacaoResponse> listar() {
        return service.listar(TenantContext.currentEmpresaId(), TenantContext.currentUsername());
    }

    @GetMapping("/contagem-nao-lidas")
    public ContagemNotificacoesResponse contagemNaoLidas() {
        return new ContagemNotificacoesResponse(
                service.contarNaoLidas(TenantContext.currentEmpresaId(), TenantContext.currentUsername()));
    }
}
