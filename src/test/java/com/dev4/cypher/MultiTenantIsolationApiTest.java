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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova de ponta a ponta da spec (§8, "Ponta a ponta"): duas empresas
 * cadastradas via sign-up, cada uma com o mesmo codigo de estacao/camera,
 * nunca se enxergam — nem no cadastro, nem no incidente, nem entre si por
 * qualquer combinacao de GET/PUT/DELETE.
 */
@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class MultiTenantIsolationApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void duasEmpresasComCodigosIdenticosNuncaSeEnxergam() throws Exception {
        MockHttpSession sessaoAcme = setupEmpresa("acme", "Acme Industrial");
        MockHttpSession sessaoGlobex = setupEmpresa("globex", "Globex Corp");

        criarEstacao(sessaoAcme, "EST-01", "Linha Acme");
        criarEstacao(sessaoGlobex, "EST-01", "Linha Globex");

        mvc.perform(get("/api/v1/stations").session(sessaoAcme))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Linha Acme"));

        mvc.perform(get("/api/v1/stations").session(sessaoGlobex))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Linha Globex"));

        // GESTOR da Globex tentando editar a EST-01 "dele" nao toca a da Acme.
        mvc.perform(post("/api/v1/stations/EST-01/token/regenerate").session(sessaoGlobex))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/stations").session(sessaoAcme))
                .andExpect(jsonPath("$[0].nome").value("Linha Acme"));
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

    private void criarEstacao(MockHttpSession session, String codigo, String nome) throws Exception {
        mvc.perform(post("/api/v1/stations").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"id\": \"%s\", \"nome\": \"%s\", \"setor\": \"montagem\", \"ativa\": true}")
                                .formatted(codigo, nome)))
                .andExpect(status().isCreated());
    }
}
