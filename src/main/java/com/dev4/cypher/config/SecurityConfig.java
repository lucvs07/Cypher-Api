package com.dev4.cypher.config;

import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.security.CypherKeyAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Infra de segurança de E4 (spec §2), introduzida em etapas: a Tarefa 1
 * acrescentou o starter com cadeia 100% permissiva; esta tarefa liga a
 * persistência de sessão (`SecurityContextRepository`) usada por
 * `SessionAuthenticator` (por sua vez usado por `AuthController` e
 * `SetupController`) para autenticar GESTOR/OPERADOR. Travar os endpoints já
 * existentes e tornar X-Cypher-Key obrigatório na ingestão continuam sendo
 * sempre as duas últimas tarefas do plano (spec §5/§7) — a cadeia abaixo
 * ainda libera tudo (`permitAll`).
 *
 * <p>{@code securityContextRepository} precisa ser o MESMO bean usado por
 * {@code SessionAuthenticator} para gravar a sessão e pela cadeia (via
 * {@code .securityContext(...)}) para restaurá-la a cada requisição — não
 * duas instâncias equivalentes de {@code HttpSessionSecurityContextRepository}.
 * Senão a gravação explícita do controller e a leitura automática do
 * {@code SecurityContextHolderFilter} são coincidentemente compatíveis, não
 * contratualmente garantidas.
 *
 * <p>{@code frameOptions().sameOrigin()} existe só por causa do
 * /h2-console: o padrão do Spring Security (X-Frame-Options: DENY) quebra
 * a página, que roda dentro de um iframe.
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
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
