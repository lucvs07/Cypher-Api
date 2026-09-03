package com.dev4.cypher;

import com.dev4.cypher.security.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class WebhookControllerApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void cadastraListaEEditaUmAssinante() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);

        mvc.perform(post("/api/v1/webhooks").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://exemplo.com/hook\", \"secret\": \"segredo123\", "
                                + "\"minSeverity\": 3, \"ativo\": true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("https://exemplo.com/hook"))
                .andExpect(jsonPath("$.secret").value("segredo123"))
                .andExpect(jsonPath("$.ativo").value(true));

        String corpo = mvc.perform(get("/api/v1/webhooks").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        Long id = com.jayway.jsonpath.JsonPath.read(corpo, "$[0].id") instanceof Integer i
                ? i.longValue() : (Long) com.jayway.jsonpath.JsonPath.read(corpo, "$[0].id");

        mvc.perform(put("/api/v1/webhooks/" + id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://exemplo.com/hook\", \"secret\": \"novo-segredo\", "
                                + "\"minSeverity\": 2, \"ativo\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("novo-segredo"))
                .andExpect(jsonPath("$.minSeverity").value(2));
    }

    @Test
    void urlDuplicadaDevolve409() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        String corpo = "{\"url\": \"https://exemplo.com/duplicada\", \"secret\": \"x\", "
                + "\"minSeverity\": 0, \"ativo\": true}";

        mvc.perform(post("/api/v1/webhooks").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/webhooks").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict());
    }

    @Test
    void minSeverityForaDeFaixaDevolve400() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);

        mvc.perform(post("/api/v1/webhooks").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://exemplo.com/x\", \"secret\": \"segredo\", "
                                + "\"minSeverity\": 5, \"ativo\": true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void secretVazioDevolve400() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);

        mvc.perform(post("/api/v1/webhooks").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://exemplo.com/y\", \"secret\": \"\", "
                                + "\"minSeverity\": 0, \"ativo\": true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void desativarEhSoftDeleteEDesapareceDaListagem() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        String corpo = mvc.perform(post("/api/v1/webhooks").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://exemplo.com/desativar\", \"secret\": \"x\", "
                                + "\"minSeverity\": 0, \"ativo\": true}"))
                .andReturn().getResponse().getContentAsString();
        Number id = com.jayway.jsonpath.JsonPath.read(corpo, "$.id");

        mvc.perform(delete("/api/v1/webhooks/" + id).session(session))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/webhooks").session(session))
                .andExpect(jsonPath("$[?(@.url=='https://exemplo.com/desativar')]").isEmpty());
    }

    @Test
    void assinanteDeUmaEmpresaNaoApareceEditaOuDesativaPorOutra() throws Exception {
        MockHttpSession sessaoAcme = setupEmpresa("acme-wh", "Acme");
        MockHttpSession sessaoGlobex = setupEmpresa("globex-wh", "Globex");

        String corpo = mvc.perform(post("/api/v1/webhooks").session(sessaoAcme)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://exemplo.com/acme-hook\", \"secret\": \"x\", "
                                + "\"minSeverity\": 0, \"ativo\": true}"))
                .andReturn().getResponse().getContentAsString();
        Number idDaAcme = com.jayway.jsonpath.JsonPath.read(corpo, "$.id");

        mvc.perform(get("/api/v1/webhooks").session(sessaoGlobex))
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(put("/api/v1/webhooks/" + idDaAcme).session(sessaoGlobex)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://exemplo.com/roubado\", \"secret\": \"x\", "
                                + "\"minSeverity\": 0, \"ativo\": true}"))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/webhooks/" + idDaAcme).session(sessaoGlobex))
                .andExpect(status().isNotFound());
    }

    private MockHttpSession setupEmpresa(String empresaId, String empresaNome) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/v1/setup").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"username\": \"gestor\", \"password\": \"senha123\", "
                                + "\"empresaId\": \"%s\", \"empresaNome\": \"%s\"}").formatted(empresaId, empresaNome)))
                .andExpect(status().isOk());
        return session;
    }
}
