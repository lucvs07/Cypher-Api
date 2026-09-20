package com.dev4.cypher.service;

import com.dev4.cypher.dto.EpiChecklistRequest;
import com.dev4.cypher.dto.EpiChecklistResponse;
import com.dev4.cypher.model.Empresa;
import com.dev4.cypher.repository.EmpresaRepository;
import com.dev4.cypher.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

/**
 * Checklist global de EPI da empresa — escopo default usado pelo pipeline
 * quando a pessoa não está numa zona com {@code required_ppe} próprio (zona,
 * quando resolve algum item, tem prioridade sobre este checklist).
 */
@Service
public class EmpresaConfigService {

    private final EmpresaRepository empresas;

    public EmpresaConfigService(EmpresaRepository empresas) {
        this.empresas = empresas;
    }

    @Transactional(readOnly = true)
    public EpiChecklistResponse epiChecklist() {
        Empresa empresa = empresaAtual();
        return new EpiChecklistResponse(empresa.getEpisMonitorados());
    }

    @Transactional
    public EpiChecklistResponse atualizarEpiChecklist(EpiChecklistRequest req) {
        Empresa empresa = empresaAtual();
        empresa.setEpisMonitorados(new ArrayList<>(req.epis()));
        empresas.saveAndFlush(empresa);
        return new EpiChecklistResponse(empresa.getEpisMonitorados());
    }

    private Empresa empresaAtual() {
        return empresas.findById(TenantContext.currentEmpresaId())
                .orElseThrow(() -> new IllegalStateException("Empresa autenticada nao encontrada"));
    }
}
