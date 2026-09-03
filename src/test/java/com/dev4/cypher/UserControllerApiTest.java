package com.dev4.cypher;

import com.dev4.cypher.repository.UsuarioRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.dev4.cypher.security.AuthTestSupport.loginGestorBootstrap;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class UserControllerApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    private MockHttpSession gestor;

    private static final String USUARIO = """
            {"username": "gestor2", "papel": "GESTOR", "password": "senha123"}
            """;

    @BeforeEach
    void setUp() throws Exception {
        gestor = loginGestorBootstrap(mvc);
        usuarios.findByUsername("gestor2@acme-test").ifPresent(u -> usuarios.deleteById(u.getId()));
    }

    @Test
    void cadastraUsuarioEDevolve201SemExporSenha() throws Exception {
        mvc.perform(post("/api/v1/users").session(gestor)
                        .contentType(MediaType.APPLICATION_JSON).content(USUARIO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("gestor2@acme-test"))
                .andExpect(jsonPath("$.papel").value("GESTOR"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void papelOperadorSemEstacaoIdDevolve422() throws Exception {
        mvc.perform(post("/api/v1/users").session(gestor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"op1\", \"papel\": \"OPERADOR\", \"password\": \"x\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void papelOperadorComEstacaoValidaDevolve201() throws Exception {
        mvc.perform(post("/api/v1/stations").session(gestor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-80\", \"nome\": \"Estacao 80\", \"setor\": \"montagem\"}"));

        mvc.perform(post("/api/v1/users").session(gestor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"op1\", \"papel\": \"OPERADOR\", \"password\": \"x\", \"estacaoId\": \"EST-80\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("OPERADOR"))
                .andExpect(jsonPath("$.estacaoId").value("EST-80"))
                .andExpect(jsonPath("$.username").value("op1@acme-test"));
    }

    @Test
    void papelOperadorComEstacaoInexistenteDevolve422() throws Exception {
        mvc.perform(post("/api/v1/users").session(gestor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"op1\", \"papel\": \"OPERADOR\", \"password\": \"x\", \"estacaoId\": \"EST-99\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void usernameDuplicadoDevolve409() throws Exception {
        mvc.perform(post("/api/v1/users").session(gestor)
                .contentType(MediaType.APPLICATION_JSON).content(USUARIO));

        mvc.perform(post("/api/v1/users").session(gestor)
                        .contentType(MediaType.APPLICATION_JSON).content(USUARIO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value(Matchers.containsString("gestor2@acme-test")));
    }

    @Test
    void listaUsuarios() throws Exception {
        mvc.perform(post("/api/v1/users").session(gestor)
                .contentType(MediaType.APPLICATION_JSON).content(USUARIO));

        mvc.perform(get("/api/v1/users").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username=='gestor2@acme-test')]").isNotEmpty());
    }

    @Test
    void editaUsernameSemInformarSenha() throws Exception {
        mvc.perform(post("/api/v1/users").session(gestor)
                .contentType(MediaType.APPLICATION_JSON).content(USUARIO));

        mvc.perform(put("/api/v1/users/{username}", "gestor2@acme-test").session(gestor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"gestor2-renomeado@acme-test\", \"papel\": \"GESTOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("gestor2-renomeado@acme-test"));
    }

    @Test
    void removeUsuarioEDevolve204() throws Exception {
        mvc.perform(post("/api/v1/users").session(gestor)
                .contentType(MediaType.APPLICATION_JSON).content(USUARIO));

        mvc.perform(delete("/api/v1/users/{username}", "gestor2@acme-test").session(gestor))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/users").session(gestor))
                .andExpect(jsonPath("$[?(@.username=='gestor2@acme-test')]").isEmpty());
    }

    @Test
    void removerInexistenteDevolve404() throws Exception {
        mvc.perform(delete("/api/v1/users/nao-existe").session(gestor)).andExpect(status().isNotFound());
    }

    @Test
    void putEDeleteDeUsuarioDeOutroTenantDevolvem404ENaoAlteramAVitima() throws Exception {
        // Gestor de outro tenant, com uma vitima "victim@outro-tenant".
        mvc.perform(post("/api/v1/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"victim\", \"password\": \"victimpass123\", "
                                + "\"empresaId\": \"outro-tenant\", \"empresaNome\": \"Outro Tenant\"}"))
                .andExpect(status().isOk());
        String hashOriginal = usuarios.findByUsername("victim@outro-tenant").orElseThrow().getPasswordHash();

        // gestor@acme-test tenta assumir a conta de victim@outro-tenant: o
        // corpo manda um username @acme-test (passaria no antigo endsWith
        // ad-hoc), mas o alvo na URL pertence a outro tenant.
        mvc.perform(put("/api/v1/users/{username}", "victim@outro-tenant").session(gestor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"victim@acme-test\", \"papel\": \"GESTOR\", \"password\": \"hacked123\"}"))
                .andExpect(status().isNotFound());

        var vitimaAposPut = usuarios.findByUsername("victim@outro-tenant");
        assertThat(vitimaAposPut).isPresent();
        assertThat(vitimaAposPut.get().getPasswordHash()).isEqualTo(hashOriginal);

        mvc.perform(delete("/api/v1/users/{username}", "victim@outro-tenant").session(gestor))
                .andExpect(status().isNotFound());

        assertThat(usuarios.findByUsername("victim@outro-tenant")).isPresent();
    }
}
