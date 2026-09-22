package com.dev4.cypher.security;

import com.dev4.cypher.model.McpCredential;
import com.dev4.cypher.repository.McpCredentialRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Autentica requisicoes ao servidor MCP (/mcp/**) via header X-Cypher-Mcp-Key,
 * formato "{id}.{secretPlano}". Diferente de CypherKeyAuthenticationFilter
 * (best-effort fora da rota de ingestao), aqui a chave e sempre obrigatoria:
 * ausente/invalida/revogada -> 401 direto, sem seguir a chain.
 */
public class McpKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Cypher-Mcp-Key";

    private final McpCredentialRepository credenciais;
    private final PasswordEncoder encoder;

    public McpKeyAuthenticationFilter(McpCredentialRepository credenciais, PasswordEncoder encoder) {
        this.credenciais = credenciais;
        this.encoder = encoder;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/mcp")) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<McpCredential> credencial = autenticar(request.getHeader(HEADER));
        if (credencial.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        McpCredential c = credencial.get();
        c.setUltimoUsoEm(OffsetDateTime.now());
        credenciais.save(c);

        SecurityContextHolder.getContext().setAuthentication(
                new McpAuthentication(c.getId(), c.getEmpresaId()));
        filterChain.doFilter(request, response);
    }

    private Optional<McpCredential> autenticar(String header) {
        if (header == null || !header.contains(".")) {
            return Optional.empty();
        }
        String[] partes = header.split("\\.", 2);
        Long id;
        try {
            id = Long.parseLong(partes[0]);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        String secretPlano = partes[1];

        return credenciais.findById(id)
                .filter(McpCredential::isAtiva)
                .filter(c -> encoder.matches(secretPlano, c.getSecretHash()));
    }
}
