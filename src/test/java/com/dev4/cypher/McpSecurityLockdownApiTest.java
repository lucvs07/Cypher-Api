package com.dev4.cypher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.dev4.cypher.security.AuthTestSupport.loginGestorBootstrap;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class McpSecurityLockdownApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void rotaMcpSemHeaderRetorna401() throws Exception {
        mvc.perform(post("/mcp")).andExpect(status().isUnauthorized());
    }

    @Test
    void rotaMcpComChaveInvalidaRetorna401() throws Exception {
        mvc.perform(post("/mcp").header("X-Cypher-Mcp-Key", "999.inexistente"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Sessão de GESTOR autenticada, mas sem o header X-Cypher-Mcp-Key: o
     * McpKeyAuthenticationFilter barra com 401 ANTES da camada de
     * autorização ser avaliada (McpKeyAuthenticationFilter não olha o
     * SecurityContext pré-existente — ver doFilterInternal) — ou seja, ter
     * sessão de GESTOR não "vaza" acesso a /mcp por faltar a chave MCP.
     * Este teste, sozinho, NÃO discrimina a regra
     * `/mcp/** -> hasRole("MCP")` do SecurityConfig: o resultado seria
     * idêntico (401) com ou sem essa regra, porque o filtro nunca deixa a
     * requisição chegar na autorização quando falta a chave. Quem prova a
     * regra é o teste abaixo, com uma chave MCP válida.
     */
    @Test
    void gestorAutenticadoSemChaveMcpRetorna401() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);
        mvc.perform(post("/mcp").session(gestor))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Prova real da regra `/mcp/** -> hasRole("MCP")`: cria uma credencial
     * MCP válida via a API real (autenticado como GESTOR) e usa a chave
     * gerada ("{id}.{secretPlano}") no header X-Cypher-Mcp-Key. Com isso o
     * McpKeyAuthenticationFilter autentica com sucesso como McpAuthentication
     * (que concede só ROLE_MCP, não ROLE_GESTOR) e repassa a requisição para
     * a camada de autorização — só aí a regra desta task entra em jogo.
     *
     * <p>Renomeado/atualizado na revisão final de branch (achado IMPORTANT):
     * a config {@code spring.ai.mcp.server} não existia em
     * {@code src/test/resources/application.yaml}, então nenhum teste
     * exercitava a rota real — MCD, que monta o handler de verdade, já
     * tinha sido implementada, mas o teste continuava escrito para a era
     * "sem handler" (esperava 404). Com a config adicionada, o handler real
     * é {@code WebMvcStreamableServerTransportProvider}, e uma requisição
     * SEM os headers exigidos pelo transporte Streamable HTTP (Accept
     * precisa aceitar tanto {@code text/event-stream} quanto
     * {@code application/json}) é rejeitada com 400 — verificado
     * manualmente lendo o corpo da resposta real
     * ({@code {"jsonRpcError":{"code":-32601,"message":"Invalid Accept
     * headers. Expected TEXT_EVENT_STREAM and APPLICATION_JSON"}...}}),
     * não um 404 de "rota inexistente". Isso já basta para discriminar a
     * regra desta task (autorizado -> chega no handler -> 400 de protocolo;
     * sem a regra, cairia no catch-all `anyRequest().hasRole("GESTOR")` ->
     * McpAuthentication sem ROLE_GESTOR -> 403).
     *
     * <p>Um segundo teste abaixo ({@link
     * #gestorComChaveMcpValidaEHandshakeValidoIniciaSessaoMcp}) vai além:
     * envia os headers e o corpo JSON-RPC corretos (`initialize`) e prova
     * que o handshake MCP completo funciona de ponta a ponta (200).
     */
    @Test
    void gestorComChaveMcpValidaMasSemHeadersDeProtocoloRecebeBadRequestDoHandlerReal() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);

        MvcResult criacao = mvc.perform(post("/api/v1/mcp-credentials").session(gestor)
                        .contentType("application/json")
                        .content("{\"nome\":\"Teste Lockdown\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String apiKey = com.jayway.jsonpath.JsonPath.read(
                criacao.getResponse().getContentAsString(), "$.apiKey");

        mvc.perform(post("/mcp").header("X-Cypher-Mcp-Key", apiKey))
                .andExpect(status().isBadRequest());
    }

    /**
     * Handshake MCP real e completo: chave válida + headers exigidos pelo
     * transporte Streamable HTTP ({@code Accept: application/json,
     * text/event-stream}) + corpo JSON-RPC {@code initialize} válido — prova
     * que o endpoint /mcp montado por MCD de fato responde ao protocolo, não
     * só que a rota existe.
     */
    @Test
    void gestorComChaveMcpValidaEHandshakeValidoIniciaSessaoMcp() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);

        MvcResult criacao = mvc.perform(post("/api/v1/mcp-credentials").session(gestor)
                        .contentType("application/json")
                        .content("{\"nome\":\"Teste Handshake\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String apiKey = com.jayway.jsonpath.JsonPath.read(
                criacao.getResponse().getContentAsString(), "$.apiKey");

        String initialize = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                + "{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"teste-lockdown\",\"version\":\"1.0\"}}}";

        mvc.perform(post("/mcp")
                        .header("X-Cypher-Mcp-Key", apiKey)
                        .header("Accept", "application/json, text/event-stream")
                        .contentType("application/json")
                        .content(initialize))
                .andExpect(status().isOk());
    }
}
