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
     * a camada de autorização — só aí a regra desta task entra em jogo:
     *
     * <ul>
     *   <li>COM a regra: McpAuthentication tem ROLE_MCP -> autorizado ->
     *       chega no dispatcher -> sem handler em /mcp (MCD, que monta o
     *       endpoint de verdade, ainda não foi implementada) -> 404. Isso
     *       deve virar 200/outro código de sucesso quando MCD existir.</li>
     *   <li>SEM a regra: cai no catch-all anyRequest().hasRole("GESTOR") ->
     *       McpAuthentication não tem ROLE_GESTOR -> AccessDeniedException
     *       -> 403.</li>
     * </ul>
     *
     * 403 vs não-403 é o sinal que de fato discrimina a regra (diferente dos
     * dois testes acima, que dão 401 em ambos os cenários). Verificado
     * manualmente: comentando a linha
     * `.requestMatchers("/mcp/**").hasRole("MCP")` em SecurityConfig, este
     * teste passa a observar 403; com a linha presente, observa 404 — ver
     * task-4-report.md para o registro dessa verificação.
     */
    @Test
    void gestorComChaveMcpValidaAutorizaERecaiEmNotFoundPorFaltaDeHandler() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);

        MvcResult criacao = mvc.perform(post("/api/v1/mcp-credentials").session(gestor)
                        .contentType("application/json")
                        .content("{\"nome\":\"Teste Lockdown\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String apiKey = com.jayway.jsonpath.JsonPath.read(
                criacao.getResponse().getContentAsString(), "$.apiKey");

        mvc.perform(post("/mcp").header("X-Cypher-Mcp-Key", apiKey))
                .andExpect(status().isNotFound());
    }
}
