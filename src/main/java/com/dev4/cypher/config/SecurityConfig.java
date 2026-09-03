package com.dev4.cypher.config;

import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.security.CypherKeyAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsUtils;

/**
 * Infra de segurança de E4 (spec §2), completa nesta tarefa: a cadeia
 * abaixo é a tradução direta da tabela de endpoints/proteção (spec §2.6) —
 * cada `requestMatchers` é uma linha da tabela, e o que não está listado
 * explicitamente cai no catch-all GESTOR-only no final (nega por padrão).
 * Ingestão (`POST`/`PATCH /api/v1/incidents`) fica de fora de propósito:
 * continua `permitAll` até a Tarefa 13, sempre a última do plano (spec
 * §5/§7) — exigir X-Cypher-Key antes disso quebraria qualquer pipeline já
 * em produção que ainda não manda o header.
 *
 * <p>{@code exceptionHandling(...)} configura o `AuthenticationEntryPoint`
 * para responder 401: é o que faz o `ExceptionTranslationFilter` do Spring
 * Security devolver 401 a uma sessão anônima (em vez do 403 do
 * `Http403ForbiddenEntryPoint` padrão) e, ao mesmo tempo, continuar
 * devolvendo 403 a uma sessão real com papel insuficiente — o filtro já
 * distingue os dois casos sozinho a partir da mesma `AccessDeniedException`,
 * sem precisar de duas cadeias de regra nem de desabilitar autenticação
 * anônima.
 *
 * <p>{@code securityContextRepository} precisa ser o MESMO bean usado por
 * {@code SessionAuthenticator} para gravar a sessão (Tarefa 7, consumido por
 * {@code AuthController} e {@code SetupController}). {@code
 * frameOptions().sameOrigin()} existe só por causa do /h2-console.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SecurityContextRepository securityContextRepository,
                                           CameraRepository cameras) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterAfter(new CypherKeyAuthenticationFilter(cameras), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                        // response.sendError(401) no entry point abaixo dispara um
                        // ERROR dispatch para /error num container real (o MockMvc da
                        // suite não reproduz esse redespacho, então os testes desta
                        // tarefa não o exercitam). Sem esta linha, o catch-all
                        // anyRequest().hasRole("GESTOR") avaliaria esse dispatch
                        // anônimo e devolveria 403 no lugar do 401 pretendido.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/setup").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/login-estacao")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/incidents").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/incidents/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").hasAnyRole("GESTOR", "OPERADOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").hasAnyRole("GESTOR", "OPERADOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/cameras/*/config")
                        .hasAnyRole("PIPELINE", "GESTOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/cameras", "/api/v1/stations")
                        .hasAnyRole("GESTOR", "OPERADOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/incidents", "/api/v1/incidents/*")
                        .hasAnyRole("GESTOR", "OPERADOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/incidents/*/status")
                        .hasAnyRole("GESTOR", "OPERADOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/incidents/*/analyze")
                        .hasRole("GESTOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/incidents/*/analyze")
                        .hasRole("GESTOR")
                        .anyRequest().hasRole("GESTOR"))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)));
        return http.build();
    }
}
