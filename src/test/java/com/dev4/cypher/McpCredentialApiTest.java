package com.dev4.cypher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.dev4.cypher.security.AuthTestSupport.loginGestorBootstrap;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class McpCredentialApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void crudCompletoDeCredencialMcp() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);

        MvcResult criacao = mvc.perform(post("/api/v1/mcp-credentials").session(gestor)
                        .contentType("application/json")
                        .content("{\"nome\":\"Claude Desktop do Teste\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").isNotEmpty())
                .andReturn();

        String corpo = criacao.getResponse().getContentAsString();
        Number id = com.jayway.jsonpath.JsonPath.read(corpo, "$.id");

        mvc.perform(get("/api/v1/mcp-credentials").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].apiKey").doesNotExist());

        mvc.perform(delete("/api/v1/mcp-credentials/" + id).session(gestor))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/mcp-credentials?incluirInativas=true").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].ativa").value(false));
    }

    @Test
    void semSessaoDeGestorRetorna401() throws Exception {
        mvc.perform(get("/api/v1/mcp-credentials"))
                .andExpect(status().isUnauthorized());
    }
}
