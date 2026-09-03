package com.dev4.cypher.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/** Sessão de GESTOR: usuário/senha, sem estacaoId (spec E4 §2.2). */
public class GestorAuthentication extends AbstractAuthenticationToken {

    private final String username;
    private final String empresaId;

    public GestorAuthentication(String username, String empresaId) {
        super(List.of(new SimpleGrantedAuthority("ROLE_GESTOR")));
        this.username = username;
        this.empresaId = empresaId;
        setAuthenticated(true);
    }

    /** Nunca guarda a senha depois de autenticar. */
    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return username;
    }

    public String getUsername() {
        return username;
    }

    public String getEmpresaId() {
        return empresaId;
    }
}
