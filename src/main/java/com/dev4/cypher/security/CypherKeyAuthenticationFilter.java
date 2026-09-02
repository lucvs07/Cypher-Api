package com.dev4.cypher.security;

import com.dev4.cypher.model.Camera;
import com.dev4.cypher.repository.CameraRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
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

    /**
     * Casadores das rotas de ingestão, resolvidos sobre o path DECODIFICADO
     * que o Spring MVC de fato usa para rotear (via PathPatternParser),
     * não sobre {@code request.getRequestURI()} bruto. Isso fecha o bypass em
     * que {@code POST /api/v1/incident%73} (percent-encoding de "incidents")
     * é roteado pelo Spring para o mesmo handler de criação de incidente, mas
     * uma comparação de string crua contra o URI não decodificado não bate —
     * deixando a rota passar sem exigir o header. {@code PathPatternRequestMatcher}
     * decodifica/normaliza o path antes de casar, então resolve o mesmo
     * request da mesma forma que o {@code DispatcherServlet} vai rotear.
     */
    private static final RequestMatcher CRIAR_INCIDENTE =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/incidents");

    /** PATCH /api/v1/incidents/{incidentKey} — exatamente um segmento, não qualquer sub-path. */
    private static final RequestMatcher FECHAR_INCIDENTE =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.PATCH, "/api/v1/incidents/{incidentKey}");

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
                SecurityContextHolder.getContext().setAuthentication(
                        new PipelineAuthentication(c.getId(), c.getEmpresaId())));

        if (isRotaDeIngestao(request) && camera.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** POST /api/v1/incidents (criação) ou PATCH /api/v1/incidents/{incidentKey} (fechamento). */
    private boolean isRotaDeIngestao(HttpServletRequest request) {
        return CRIAR_INCIDENTE.matches(request) || FECHAR_INCIDENTE.matches(request);
    }
}
