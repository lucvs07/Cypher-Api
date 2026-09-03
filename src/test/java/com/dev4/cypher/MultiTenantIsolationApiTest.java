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

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova de ponta a ponta da spec (§8, "Ponta a ponta"): duas empresas
 * cadastradas via sign-up, cada uma com o mesmo codigo de estacao, nunca se
 * enxergam — nem no cadastro/listagem de estacao, nem ao regenerar o token
 * "da mesma" estacao (linhas distintas por baixo do codigo identico).
 *
 * <p>Cobertura complementar: isolamento de incidentes em
 * {@code IncidentCrossTenantScopeApiTest}, de resumo/heatmap em
 * {@code SummaryApiTest}.
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

        String tokenAcmeAntes = criarEstacao(sessaoAcme, "EST-01", "Linha Acme");
        String tokenGlobexAntes = criarEstacao(sessaoGlobex, "EST-01", "Linha Globex");

        mvc.perform(get("/api/v1/stations").session(sessaoAcme))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Linha Acme"));

        mvc.perform(get("/api/v1/stations").session(sessaoGlobex))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Linha Globex"));

        // GESTOR da Globex regenera o token da EST-01 "dele" — nao pode tocar
        // no token da EST-01 da Acme, ainda que o codigo seja identico.
        mvc.perform(post("/api/v1/stations/EST-01/token/regenerate").session(sessaoGlobex))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(tokenGlobexAntes)));

        // PUT nao mexe no token — serve so pra ler de volta o token atual da
        // Acme e confirmar que o regenerate da Globex nao vazou pra ela.
        mvc.perform(put("/api/v1/stations/EST-01").session(sessaoAcme)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"EST-01\", \"nome\": \"Linha Acme\", \"setor\": \"montagem\", \"ativa\": true}"))
                .andExpect(jsonPath("$.nome").value("Linha Acme"))
                .andExpect(jsonPath("$.token").value(tokenAcmeAntes));
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

    /** @return o token gerado pra estacao recem-criada. */
    private String criarEstacao(MockHttpSession session, String codigo, String nome) throws Exception {
        String corpo = mvc.perform(post("/api/v1/stations").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"id\": \"%s\", \"nome\": \"%s\", \"setor\": \"montagem\", \"ativa\": true}")
                                .formatted(codigo, nome)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(corpo, "$.token");
    }
}
