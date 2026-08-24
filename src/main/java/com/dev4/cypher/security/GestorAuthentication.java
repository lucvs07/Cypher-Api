package com.dev4.cypher.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/** Sessão de GESTOR: usuário/senha, sem estacaoId (spec E4 §2.2). */
public class GestorAuthentication extends AbstractAuthenticationToken {

    private final String username;

    public GestorAuthentication(String username) {
        super(List.of(new SimpleGrantedAuthority("ROLE_GESTOR")));
        this.username = username;
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
}
