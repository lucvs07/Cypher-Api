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
 *
 * <p>Rotaciona o id da sessão ({@code request.changeSessionId()}) antes de
 * gravar o {@code SecurityContext}, para os três chamadores
 * (`AuthController.login`, `.loginEstacao`, `SetupController.setup`) ao
 * mesmo tempo, sem duplicar a chamada em cada um. Isso evita fixação de
 * sessão: sem a rotação, um id de sessão plantado por um atacante antes do
 * login continuaria válido — e autenticado — depois dele.
 */
@Component
public class SessionAuthenticator {

    private final SecurityContextRepository securityContextRepository;

    public SessionAuthenticator(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    public void autenticar(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        request.getSession();
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
