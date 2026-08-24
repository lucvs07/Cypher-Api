package com.dev4.cypher.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Infra de segurança de E4 (spec §2), introduzida em etapas: esta primeira
 * versão só acrescenta o starter e mantém todo endpoint aberto (permitAll),
 * para que a suíte inteira continue passando sem quebrar nenhum cliente já
 * existente. As tarefas seguintes desta entrega apertam as regras aos
 * poucos; travar os endpoints já existentes e tornar X-Cypher-Key
 * obrigatório na ingestão são sempre as duas últimas tarefas do plano
 * (spec §5/§7).
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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
