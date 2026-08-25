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
 * Camera.apiKey (spec E4 §2.3). Nas rotas de ingestão (POST/PATCH
 * /api/v1/incidents — nunca PUT .../status, que é ação do operador humano,
 * E3) o header agora é OBRIGATÓRIO: ausente ou inválido devolve 401 e
 * encerra a cadeia aqui, sem chamar {@code filterChain.doFilter}. Em
 * qualquer outra rota o comportamento continua exatamente como a Tarefa 8
 * deixou — autentica se a chave bater, segue adiante de qualquer forma.
 *
 * <p>Este é o flip final do plano (spec §5/§7): o pipeline Python
 * (`cypher-capture`) só passa a enviar o header numa entrega separada
 * (order-step 2), e essa mudança tem que já estar implantada antes deste
 * flip — por isso esta é sempre a última tarefa.
 *
 * <p>Nunca toca HttpSession — uma requisição de pipeline não carrega
 * cookie, e autenticar/rejeitar aqui não cria nem depende de sessão HTTP.
 *
 * <p>Registrado com {@code addFilterAfter(..., SecurityContextHolderFilter.class)}
 * (`SecurityConfig`, Tarefa 8): roda tecnicamente depois do filtro que
 * restaura o contexto a partir da sessão, mas nunca lê a sessão — é
 * "independente da sessão", o espírito de "antes do filtro de sessão" da
 * spec (E4 §2.3).
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
        Optional<Camera> camera = chave != null ? cameras.findByApiKey(chave) : Optional.empty();
        camera.ifPresent(c ->
                SecurityContextHolder.getContext().setAuthentication(new PipelineAuthentication(c.getId())));

        if (isRotaDeIngestao(request) && camera.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** POST /api/v1/incidents (criação) ou PATCH /api/v1/incidents/{incidentKey} (fechamento). */
    private boolean isRotaDeIngestao(HttpServletRequest request) {
        String metodo = request.getMethod();
        String caminho = request.getRequestURI();
        if ("POST".equals(metodo) && "/api/v1/incidents".equals(caminho)) {
            return true;
        }
        return "PATCH".equals(metodo) && caminho.startsWith("/api/v1/incidents/");
    }
}
