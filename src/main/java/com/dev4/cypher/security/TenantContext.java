package com.dev4.cypher.security;

/**
 * Fronteira unica entre a chave armazenada ("empresaId:codigo") e o codigo
 * puro que a API/UI enxergam (spec §2.2). Nenhum service concatena essa
 * string fora daqui.
 */
public final class TenantContext {

    /**
     * Tenant unico usado pela Tarefa 2 antes de a sessao carregar o tenant de
     * verdade. Removida na Tarefa 3, quando {@code currentEmpresaId()}
     * substitui todo uso deste valor.
     */
    public static final String DEMO_EMPRESA_ID = "demo";

    private TenantContext() {
    }

    public static String key(String empresaId, String codigo) {
        return empresaId + ":" + codigo;
    }

    /** Remove o prefixo "empresaId:" de uma chave armazenada, devolvendo so o codigo. */
    public static String codigo(String chave) {
        int i = chave.indexOf(':');
        return i < 0 ? chave : chave.substring(i + 1);
    }
}
