package com.dev4.cypher.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/** Sessão de OPERADOR: token de estação, carrega a estacaoId como detalhe (spec E4 §2.2/§2.6). */
public class OperadorAuthentication extends AbstractAuthenticationToken {

    private final String estacaoId;

    public OperadorAuthentication(String estacaoId) {
        super(List.of(new SimpleGrantedAuthority("ROLE_OPERADOR")));
        this.estacaoId = estacaoId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return estacaoId;
    }

    public String getEstacaoId() {
        return estacaoId;
    }
}
