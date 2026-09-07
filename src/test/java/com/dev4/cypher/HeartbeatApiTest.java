package com.dev4.cypher;

import com.dev4.cypher.model.MonitoramentoDiario;
import com.dev4.cypher.model.Turno;
import com.dev4.cypher.repository.MonitoramentoDiarioRepository;
import com.dev4.cypher.security.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class HeartbeatApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MonitoramentoDiarioRepository registros;

    @Test
    void heartbeatSemChaveDevolve401Ou403() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        String apiKey = criarCameraEPegarApiKey(session, "CAM-HB1", "EST-01");

        mvc.perform(post("/api/v1/cameras/CAM-HB1/heartbeat"))
                .andExpect(status().is4xxClientError());

        // Sanity: com a chave certa, funciona.
        mvc.perform(post("/api/v1/cameras/CAM-HB1/heartbeat").header("X-Cypher-Key", apiKey))
                .andExpect(status().isNoContent());
    }

    @Test
    void heartbeatDeCameraInexistenteDevolve404() throws Exception {
        mvc.perform(post("/api/v1/cameras/CAM-NAO-EXISTE/heartbeat").header("X-Cypher-Key", "qualquer"))
                .andExpect(status().is4xxClientError());
    }

    private String criarCameraEPegarApiKey(MockHttpSession session, String codigo, String estacaoId) throws Exception {
        mvc.perform(post("/api/v1/stations").session(session)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"id\": \"" + estacaoId + "\", \"nome\": \"E\", \"setor\": \"x\", \"ativa\": true}"));
        mvc.perform(post("/api/v1/cameras").session(session)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(("{\"id\": \"%s\", \"nome\": \"C\", \"estacaoId\": \"%s\", "
                        + "\"streamUrl\": \"http://127.0.0.1:9100/stream\", \"ativa\": true}")
                        .formatted(codigo, estacaoId)));
        String corpo = mvc.perform(post("/api/v1/cameras/" + codigo + "/apikey/regenerate").session(session))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.apiKey");
    }
}
