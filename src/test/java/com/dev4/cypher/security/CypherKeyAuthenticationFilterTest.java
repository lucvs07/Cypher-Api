package com.dev4.cypher.security;

import com.dev4.cypher.model.Camera;
import com.dev4.cypher.repository.CameraRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.http.HttpStatus;
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

    @Test
    void ingestaoPostSemChaveDevolve401ENaoChamaProximoFiltro() throws Exception {
        CypherKeyAuthenticationFilter filtro = new CypherKeyAuthenticationFilter(cameras);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/incidents");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] proximoFiltroChamado = {false};
        FilterChain chain = (req, res) -> proximoFiltroChamado[0] = true;

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(proximoFiltroChamado[0]).isFalse();
    }

    @Test
    void ingestaoPatchSemChaveDevolve401() throws Exception {
        CypherKeyAuthenticationFilter filtro = new CypherKeyAuthenticationFilter(cameras);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "PATCH", "/api/v1/incidents/CAM-01:run:track:20260810T000000000Z");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void ingestaoComChaveValidaSeguePraFrente() throws Exception {
        cameras.saveAndFlush(new Camera("CAM-81", "Frontal", "EST-81",
                "http://127.0.0.1:8181/stream", "rtsp://x", true, "chave-ingestao-valida"));
        CypherKeyAuthenticationFilter filtro = new CypherKeyAuthenticationFilter(cameras);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/incidents");
        request.addHeader(CypherKeyAuthenticationFilter.HEADER, "chave-ingestao-valida");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] proximoFiltroChamado = {false};
        FilterChain chain = (req, res) -> proximoFiltroChamado[0] = true;

        filtro.doFilter(request, response, chain);

        assertThat(proximoFiltroChamado[0]).isTrue();
    }

    @Test
    void rotaQueNaoEDeIngestaoContinuaPermissivaSemChave() throws Exception {
        CypherKeyAuthenticationFilter filtro = new CypherKeyAuthenticationFilter(cameras);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cameras/CAM-01/config");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] proximoFiltroChamado = {false};
        FilterChain chain = (req, res) -> proximoFiltroChamado[0] = true;

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(proximoFiltroChamado[0]).isTrue();
    }

    @Test
    void ingestaoPostComPathPercentEncodedTambemDevolve401() throws Exception {
        // Bypass real: "/api/v1/incident%73" é o mesmo path que
        // "/api/v1/incidents" depois de decodificado — e é assim que o
        // Spring MVC roteia a requisição para o mesmo handler de criação de
        // incidente. Uma comparação de string contra request.getRequestURI()
        // (bruto, não decodificado) não bate com essa variante e deixava a
        // rota passar sem exigir X-Cypher-Key. isRotaDeIngestao precisa
        // casar no path decodificado, exatamente como o Spring MVC roteia.
        CypherKeyAuthenticationFilter filtro = new CypherKeyAuthenticationFilter(cameras);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/incident%73");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] proximoFiltroChamado = {false};
        FilterChain chain = (req, res) -> proximoFiltroChamado[0] = true;

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(proximoFiltroChamado[0]).isFalse();
    }

    @Test
    void ingestaoPatchComPathPercentEncodedTambemDevolve401() throws Exception {
        CypherKeyAuthenticationFilter filtro = new CypherKeyAuthenticationFilter(cameras);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "PATCH", "/api/v1/incident%73/CAM-01:run:track:20260810T000000000Z");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] proximoFiltroChamado = {false};
        FilterChain chain = (req, res) -> proximoFiltroChamado[0] = true;

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(proximoFiltroChamado[0]).isFalse();
    }

    @Test
    void patchComSubPathAlemDoIncidentKeyNaoEIngestaoENaoExigeChave() throws Exception {
        // Achado Minor: só PATCH /api/v1/incidents/{incidentKey} (um único
        // segmento) é rota de ingestão — qualquer coisa aninhada abaixo
        // disso não é, e o antigo startsWith("/api/v1/incidents/") era
        // largo demais.
        CypherKeyAuthenticationFilter filtro = new CypherKeyAuthenticationFilter(cameras);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "PATCH", "/api/v1/incidents/CAM-01:run:track:20260810T000000000Z/extra");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] proximoFiltroChamado = {false};
        FilterChain chain = (req, res) -> proximoFiltroChamado[0] = true;

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(proximoFiltroChamado[0]).isTrue();
    }

    @Test
    void statusDeIncidenteAcaoDoOperadorNaoEIngestaoENaoExigeChave() throws Exception {
        // PUT .../status e o reconhecimento humano (E3), nao a ingestao do
        // pipeline — nunca deve exigir X-Cypher-Key.
        CypherKeyAuthenticationFilter filtro = new CypherKeyAuthenticationFilter(cameras);
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/incidents/1/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] proximoFiltroChamado = {false};
        FilterChain chain = (req, res) -> proximoFiltroChamado[0] = true;

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(proximoFiltroChamado[0]).isTrue();
    }
}
