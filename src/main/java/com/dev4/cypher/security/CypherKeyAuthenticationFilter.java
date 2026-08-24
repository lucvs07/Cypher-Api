package com.dev4.cypher.security;

import com.dev4.cypher.model.Camera;
import com.dev4.cypher.repository.CameraRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Autentica ROLE_PIPELINE a partir do header X-Cypher-Key, validado contra
 * Camera.apiKey (spec E4 §2.3). Nesta tarefa o modo é permissivo: header
 * ausente ou que não bate com nenhuma câmera não rejeita nada, a requisição
 * segue sem autenticação de pipeline — a Tarefa 13 (sempre a última do
 * plano) é quem torna o header obrigatório, e só nos endpoints de ingestão.
 *
 * <p>Nunca toca HttpSession — diferente do login por sessão (GESTOR/OPERADOR
 * via {@code AuthController}), uma requisição de pipeline não carrega
 * cookie, e autenticar aqui não cria nem depende de sessão HTTP.
 *
 * <p>Registrado com {@code addFilterAfter(..., SecurityContextHolderFilter.class)}
 * (Step 4): roda tecnicamente depois do filtro que restaura o contexto a
 * partir da sessão, mas nunca lê a sessão e só sobrescreve o
 * {@code Authentication} quando a chave é válida — é "independente da
 * sessão", o espírito de "antes do filtro de sessão" da spec (E4 §2.3): a
 * autenticação por chave nunca depende do que a sessão carregava.
 */
public class CypherKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Cypher-Key";

    private final CameraRepository cameras;

    public CypherKeyAuthenticationFilter(CameraRepository cameras) {
        this.cameras = cameras;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String chave = request.getHeader(HEADER);
        if (chave != null) {
            Optional<Camera> camera = cameras.findByApiKey(chave);
            camera.ifPresent(c ->
                    SecurityContextHolder.getContext().setAuthentication(new PipelineAuthentication(c.getId())));
        }
        filterChain.doFilter(request, response);
    }
}
