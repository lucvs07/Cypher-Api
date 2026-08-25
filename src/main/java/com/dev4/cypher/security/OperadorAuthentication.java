package com.dev4.cypher.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Sessão de OPERADOR: por token de estação (spec E4 §2.2/§2.6) ou por
 * usuário/senha vinculado a uma estação (revisão pós-E4) — os dois carregam
 * a {@code estacaoId} como detalhe; só o segundo tem {@code username}.
 */
public class OperadorAuthentication extends AbstractAuthenticationToken {

    private final String estacaoId;
    private final String username;

    /** Login por token — sem conceito de usuário. */
    public OperadorAuthentication(String estacaoId) {
        this(estacaoId, null);
    }

    /** Login por usuário/senha (revisão pós-E4). */
    public OperadorAuthentication(String estacaoId, String username) {
        super(List.of(new SimpleGrantedAuthority("ROLE_OPERADOR")));
        this.estacaoId = estacaoId;
        this.username = username;
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

    public String getUsername() {
        return username;
    }
}
