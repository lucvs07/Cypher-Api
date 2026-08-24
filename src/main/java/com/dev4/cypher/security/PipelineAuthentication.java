package com.dev4.cypher.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/** Autenticação sem sessão do pipeline, via X-Cypher-Key validado contra Camera.apiKey (spec E4 §2.3). */
public class PipelineAuthentication extends AbstractAuthenticationToken {

    private final String cameraId;

    public PipelineAuthentication(String cameraId) {
        super(List.of(new SimpleGrantedAuthority("ROLE_PIPELINE")));
        this.cameraId = cameraId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return cameraId;
    }

    public String getCameraId() {
        return cameraId;
    }
}
