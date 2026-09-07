package com.dev4.cypher;

import com.dev4.cypher.security.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlacarApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void semSessaoRecusa() throws Exception {
        mvc.perform(get("/api/v1/placar")).andExpect(status().is4xxClientError());
    }

    @Test
    void gestorAutenticadoRecebe200() throws Exception {
        MockHttpSession gestor = AuthTestSupport.loginGestorBootstrap(mvc);
        mvc.perform(get("/api/v1/placar").session(gestor)).andExpect(status().isOk());
    }

    @Test
    void operadorRecebe403() throws Exception {
        MockHttpSession gestor = AuthTestSupport.loginGestorBootstrap(mvc);
        mvc.perform(post("/api/v1/stations").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-PLC\", \"nome\": \"A\", \"setor\": \"s\"}"));
        String token = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/v1/stations/EST-PLC/token/regenerate").session(gestor))
                        .andReturn().getResponse().getContentAsString(),
                "$.token");
        MockHttpSession operador = AuthTestSupport.loginOperador(mvc, token);

        mvc.perform(get("/api/v1/placar").session(operador)).andExpect(status().isForbidden());
    }
}
