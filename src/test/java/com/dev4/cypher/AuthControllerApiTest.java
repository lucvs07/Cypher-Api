package com.dev4.cypher;

import com.dev4.cypher.model.Usuario;
import com.dev4.cypher.repository.EstacaoRepository;
import com.dev4.cypher.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static com.dev4.cypher.security.AuthTestSupport.GESTOR_PASSWORD;
import static com.dev4.cypher.security.AuthTestSupport.GESTOR_USERNAME;
import static com.dev4.cypher.security.AuthTestSupport.loginGestor;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EstacaoRepository estacoes;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder encoder;

    /**
     * Esta classe testa `POST /auth/login` diretamente (não via
     * `AuthTestSupport.loginGestorBootstrap`, que passa por `/setup`) — sem
     * seeder, o gestor bootstrap precisa existir antes. `existsByUsername`
     * evita duplicar (`UNIQUE` em `username`) se outra classe do mesmo
     * contexto compartilhado já o criou primeiro; sempre o mesmo username
     * (`GESTOR_USERNAME`) que `AuthTestSupport` usa, então os dois caminhos
     * nunca colidem em usernames diferentes.
     */
    @BeforeEach
    void garantirGestorBootstrap() {
        if (!usuarios.existsByUsername(GESTOR_USERNAME)) {
            usuarios.save(new Usuario(null, GESTOR_USERNAME, encoder.encode(GESTOR_PASSWORD), "GESTOR", null));
        }
    }

    @Test
    void loginComCredencialCorretaESessaoSobreviveAProximaRequisicao() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/v1/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}"
                                .formatted(GESTOR_USERNAME, GESTOR_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("GESTOR"))
                .andExpect(jsonPath("$.username").value(GESTOR_USERNAME))
                .andExpect(jsonPath("$.estacaoId").doesNotExist());

        // A prova real de que a sessão persistiu: uma SEGUNDA requisição, com o
        // mesmo MockHttpSession, enxerga a autenticação feita na primeira.
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("GESTOR"))
                .andExpect(jsonPath("$.username").value(GESTOR_USERNAME));
    }

    @Test
    void loginRotacionaOIdDaSessaoContraFixacao() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String idAntes = session.getId();

        mvc.perform(post("/api/v1/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}"
                                .formatted(GESTOR_USERNAME, GESTOR_PASSWORD)))
                .andExpect(status().isOk());

        // Um id de sessão plantado antes do login não pode sobreviver como o
        // id da sessão autenticada — senão um atacante que planta esse id na
        // vítima antes dela logar herdaria a sessão autenticada dela.
        assertNotEquals(idAntes, session.getId());
    }

    @Test
    void loginComSenhaErradaDevolve401() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"errada\"}".formatted(GESTOR_USERNAME)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void loginComUsuarioInexistenteDevolve401() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"ninguem\", \"password\": \"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void loginEstacaoComTokenValidoAutenticaComoOperadorESessaoSobrevive() throws Exception {
        MockHttpSession gestor = loginGestor(mvc, GESTOR_USERNAME, GESTOR_PASSWORD);
        mvc.perform(post("/api/v1/stations").session(gestor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-70\", \"nome\": \"Prensa 7\", \"setor\": \"estamparia\"}"));
        String token = estacoes.findById("EST-70").orElseThrow().getToken();
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/v1/auth/login-estacao").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("OPERADOR"))
                .andExpect(jsonPath("$.estacaoId").value("EST-70"))
                .andExpect(jsonPath("$.username").doesNotExist());

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("OPERADOR"))
                .andExpect(jsonPath("$.estacaoId").value("EST-70"));
    }

    @Test
    void loginEstacaoComEstacaoInativaDevolve401() throws Exception {
        MockHttpSession gestor = loginGestor(mvc, GESTOR_USERNAME, GESTOR_PASSWORD);
        mvc.perform(post("/api/v1/stations").session(gestor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-71\", \"nome\": \"Prensa 8\", \"setor\": \"estamparia\"}"));
        String token = estacoes.findById("EST-71").orElseThrow().getToken();
        mvc.perform(delete("/api/v1/stations/EST-71").session(gestor))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/login-estacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void loginEstacaoComTokenInvalidoDevolve401() throws Exception {
        mvc.perform(post("/api/v1/auth/login-estacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"token-que-nao-existe\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void loginComCredencialDeOperadorAutenticaComoOperadorComEstacaoEUsername() throws Exception {
        MockHttpSession gestorSession = loginGestor(mvc, GESTOR_USERNAME, GESTOR_PASSWORD);
        mvc.perform(post("/api/v1/stations").session(gestorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-72\", \"nome\": \"Prensa 9\", \"setor\": \"estamparia\"}"));
        mvc.perform(post("/api/v1/users").session(gestorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"op-cred\", \"papel\": \"OPERADOR\", "
                        + "\"password\": \"senhaOp123\", \"estacaoId\": \"EST-72\"}"));
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/v1/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"op-cred\", \"password\": \"senhaOp123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("OPERADOR"))
                .andExpect(jsonPath("$.estacaoId").value("EST-72"))
                .andExpect(jsonPath("$.username").value("op-cred"));

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("OPERADOR"))
                .andExpect(jsonPath("$.estacaoId").value("EST-72"))
                .andExpect(jsonPath("$.username").value("op-cred"));
    }

    @Test
    void meSemSessaoDevolve401() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEncerraASessaoEMeVoltaA401() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/v1/auth/login").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"password\": \"%s\"}"
                        .formatted(GESTOR_USERNAME, GESTOR_PASSWORD)));

        mvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }
}
