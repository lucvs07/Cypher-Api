package com.dev4.cypher.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/** Autenticação sem sessão de um agente de IA externo, via X-Cypher-Mcp-Key validado contra McpCredential. */
public class McpAuthentication extends AbstractAuthenticationToken {

    private final Long mcpCredentialId;
    private final String empresaId;

    public McpAuthentication(Long mcpCredentialId, String empresaId) {
        super(List.of(new SimpleGrantedAuthority("ROLE_MCP")));
        this.mcpCredentialId = mcpCredentialId;
        this.empresaId = empresaId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return mcpCredentialId;
    }

    public Long getMcpCredentialId() {
        return mcpCredentialId;
    }

    public String getEmpresaId() {
        return empresaId;
    }
}
