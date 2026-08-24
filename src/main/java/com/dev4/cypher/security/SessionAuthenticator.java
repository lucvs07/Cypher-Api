package com.dev4.cypher.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Único ponto que grava um {@code Authentication} na sessão HTTP — usado por
 * {@code AuthController} (`/auth/login`, `/auth/login-estacao`) e por
 * {@code SetupController} (`/setup`), para nunca ter duas cópias divergentes
 * da mesma gravação de {@code SecurityContext} (spec E4 §2.5: "reaproveita a
 * mesma... o mesmo mecanismo de sessão de `POST /auth/login`, sem duplicar a
 * lógica").
 *
 * <p>Precisa do MESMO bean de {@code SecurityContextRepository} usado pela
 * cadeia (via {@code .securityContext(...)} em `SecurityConfig`) para
 * restaurar a sessão a cada requisição — não uma segunda instância
 * equivalente de {@code HttpSessionSecurityContextRepository}.
 */
@Component
public class SessionAuthenticator {

    private final SecurityContextRepository securityContextRepository;

    public SessionAuthenticator(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    public void autenticar(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
