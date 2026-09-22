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
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Autentica requisicoes ao servidor MCP (/mcp/**) via header X-Cypher-Mcp-Key,
 * formato "{id}.{secretPlano}". Segue o mesmo padrao de CypherKeyAuthenticationFilter:
 * sempre tenta autenticar primeiro, e so bloqueia com 401 condicionalmente por rota
 * (aqui, apenas em /mcp/**, usando PathPatternRequestMatcher em vez de comparacao
 * de string crua no path, para evitar bypass por encoding).
 */
public class McpKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Cypher-Mcp-Key";

    private static final RequestMatcher ROTA_MCP =
            PathPatternRequestMatcher.withDefaults().matcher("/mcp/**");

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
        Optional<McpCredential> credencial = autenticar(request.getHeader(HEADER));
        credencial.ifPresent(c -> {
            c.setUltimoUsoEm(OffsetDateTime.now());
            credenciais.save(c);
            SecurityContextHolder.getContext().setAuthentication(
                    new McpAuthentication(c.getId(), c.getEmpresaId()));
        });

        if (ROTA_MCP.matches(request) && credencial.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
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
