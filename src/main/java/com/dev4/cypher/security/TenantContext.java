package com.dev4.cypher.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Fronteira unica entre a chave armazenada ("empresaId:codigo") e o codigo
 * puro que a API/UI enxergam (spec §2.2). Nenhum service concatena essa
 * string fora daqui.
 */
public final class TenantContext {

    private TenantContext() {
    }

    /** Le o tenant autenticado no contexto de seguranca atual (spec §3.3). */
    public static String currentEmpresaId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof GestorAuthentication g) {
            return g.getEmpresaId();
        }
        if (auth instanceof OperadorAuthentication o) {
            return o.getEmpresaId();
        }
        if (auth instanceof PipelineAuthentication p) {
            return p.getEmpresaId();
        }
        throw new IllegalStateException("Nenhum tenant autenticado no contexto de seguranca");
    }

    public static String key(String empresaId, String codigo) {
        return empresaId + ":" + codigo;
    }

    public static String key(String codigo) {
        return key(currentEmpresaId(), codigo);
    }

    /** Remove o prefixo "empresaId:" de uma chave armazenada, devolvendo so o codigo. */
    public static String codigo(String chave) {
        int i = chave.indexOf(':');
        return i < 0 ? chave : chave.substring(i + 1);
    }
}
