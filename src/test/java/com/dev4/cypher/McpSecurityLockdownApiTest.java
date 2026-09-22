package com.dev4.cypher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

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
}
