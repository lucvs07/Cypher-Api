package com.dev4.cypher.security;

import com.dev4.cypher.model.Camera;
import com.dev4.cypher.repository.CameraRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `OncePerRequestFilter` pode ser exercitado direto, sem servlet container:
 * `MockHttpServletRequest`/`MockHttpServletResponse` (do próprio Spring) e um
 * `FilterChain` de uma linha bastam — nenhum framework de mock envolvido.
 */
@DataJpaTest
class CypherKeyAuthenticationFilterTest {

    @Autowired
    private CameraRepository cameras;

    @BeforeEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void chaveValidaAutenticaComoRolePipeline() throws Exception {
        cameras.saveAndFlush(new Camera("CAM-80", "Frontal", "EST-80",
                "http://127.0.0.1:8180/stream", "rtsp://x", true, "chave-valida"));
        CypherKeyAuthenticationFilter filtro = new CypherKeyAuthenticationFilter(cameras);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CypherKeyAuthenticationFilter.HEADER, "chave-valida");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] proximoFiltroChamado = {false};
        FilterChain chain = (req, res) -> proximoFiltroChamado[0] = true;

        filtro.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isInstanceOf(PipelineAuthentication.class);
        assertThat(((PipelineAuthentication) auth).getCameraId()).isEqualTo("CAM-80");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_PIPELINE");
        assertThat(proximoFiltroChamado[0]).isTrue();
    }

    @Test
    void chaveAusenteSeguePermissivoSemAutenticar() throws Exception {
        CypherKeyAuthenticationFilter filtro = new CypherKeyAuthenticationFilter(cameras);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] proximoFiltroChamado = {false};
        FilterChain chain = (req, res) -> proximoFiltroChamado[0] = true;

        filtro.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(proximoFiltroChamado[0]).isTrue();
    }

    @Test
    void chaveInvalidaSeguePermissivoSemAutenticar() throws Exception {
        CypherKeyAuthenticationFilter filtro = new CypherKeyAuthenticationFilter(cameras);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CypherKeyAuthenticationFilter.HEADER, "chave-que-nao-existe");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filtro.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
