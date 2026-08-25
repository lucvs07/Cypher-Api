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
        usuarios.findByUsername("gestor2").ifPresent(u -> usuarios.deleteById(u.getId()));
    }

    @Test
    void cadastraUsuarioEDevolve201SemExporSenha() throws Exception {
        mvc.perform(post("/api/v1/users").session(gestor)
                        .contentType(MediaType.APPLICATION_JSON).content(USUARIO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("gestor2"))
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
                .andExpect(jsonPath("$.estacaoId").value("EST-80"));
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
                .andExpect(jsonPath("$.erro").value(Matchers.containsString("gestor2")));
    }

    @Test
    void listaUsuarios() throws Exception {
        mvc.perform(post("/api/v1/users").session(gestor)
                .contentType(MediaType.APPLICATION_JSON).content(USUARIO));

        mvc.perform(get("/api/v1/users").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username=='gestor2')]").isNotEmpty());
    }

    @Test
    void editaUsernameSemInformarSenha() throws Exception {
        mvc.perform(post("/api/v1/users").session(gestor)
                .contentType(MediaType.APPLICATION_JSON).content(USUARIO));

        mvc.perform(put("/api/v1/users/{username}", "gestor2").session(gestor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"gestor2-renomeado\", \"papel\": \"GESTOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("gestor2-renomeado"));
    }

    @Test
    void removeUsuarioEDevolve204() throws Exception {
        mvc.perform(post("/api/v1/users").session(gestor)
                .contentType(MediaType.APPLICATION_JSON).content(USUARIO));

        mvc.perform(delete("/api/v1/users/{username}", "gestor2").session(gestor))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/users").session(gestor))
                .andExpect(jsonPath("$[?(@.username=='gestor2')]").isEmpty());
    }

    @Test
    void removerInexistenteDevolve404() throws Exception {
        mvc.perform(delete("/api/v1/users/nao-existe").session(gestor)).andExpect(status().isNotFound());
    }
}
