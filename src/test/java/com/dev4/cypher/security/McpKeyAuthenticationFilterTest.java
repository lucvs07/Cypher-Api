package com.dev4.cypher.security;

import com.dev4.cypher.model.McpCredential;
import com.dev4.cypher.repository.McpCredentialRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

class McpKeyAuthenticationFilterTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void chaveValidaAutenticaComoMcpAuthentication() throws Exception {
        McpCredential credencial = new McpCredential(42L, "Teste", encoder.encode("segredo-123"),
                true, "acme-test", OffsetDateTime.now(), null);
        McpCredentialRepository repo = Mockito.mock(McpCredentialRepository.class);
        Mockito.when(repo.findById(42L)).thenReturn(Optional.of(credencial));

        McpKeyAuthenticationFilter filtro = new McpKeyAuthenticationFilter(repo, encoder);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");
        req.addHeader(McpKeyAuthenticationFilter.HEADER, "42.segredo-123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filtro.doFilterInternal(req, res, chain);

        McpAuthentication auth = (McpAuthentication) SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getMcpCredentialId()).isEqualTo(42L);
        assertThat(auth.getEmpresaId()).isEqualTo("acme-test");
        verify(chain).doFilter(req, res);
    }

    @Test
    void chaveAusenteEmRotaMcpRetorna401SemChamarAChain() throws Exception {
        McpCredentialRepository repo = Mockito.mock(McpCredentialRepository.class);
        McpKeyAuthenticationFilter filtro = new McpKeyAuthenticationFilter(repo, encoder);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filtro.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        Mockito.verifyNoInteractions(chain);
    }

    @Test
    void credencialRevogadaRetorna401() throws Exception {
        McpCredential revogada = new McpCredential(9L, "Revogada", encoder.encode("x"),
                false, "acme-test", OffsetDateTime.now(), null);
        McpCredentialRepository repo = Mockito.mock(McpCredentialRepository.class);
        Mockito.when(repo.findById(9L)).thenReturn(Optional.of(revogada));
        McpKeyAuthenticationFilter filtro = new McpKeyAuthenticationFilter(repo, encoder);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");
        req.addHeader(McpKeyAuthenticationFilter.HEADER, "9.x");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filtro.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void headerSemPontoNaoAutenticaMasNaoBloqueiaForaDeMcp() throws Exception {
        McpCredentialRepository repo = Mockito.mock(McpCredentialRepository.class);
        McpKeyAuthenticationFilter filtro = new McpKeyAuthenticationFilter(repo, encoder);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/outra-rota");
        req.addHeader(McpKeyAuthenticationFilter.HEADER, "sem-ponto-aqui");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filtro.doFilterInternal(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        Mockito.verify(chain).doFilter(req, res);
    }

    @Test
    void idNaoNumericoRetorna401EmRotaMcp() throws Exception {
        McpCredentialRepository repo = Mockito.mock(McpCredentialRepository.class);
        McpKeyAuthenticationFilter filtro = new McpKeyAuthenticationFilter(repo, encoder);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");
        req.addHeader(McpKeyAuthenticationFilter.HEADER, "abc.segredo");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filtro.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
