package com.dev4.cypher;

import com.dev4.cypher.model.Usuario;
import com.dev4.cypher.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
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
 * `@Transactional` + `deleteAll()` no `@BeforeEach` (mesmo padrão de
 * `CatalogWriteApiTest`): sem isso, `configuradoFalseQuandoNenhumUsuarioExiste`
 * seria dependente da ordem de execução da suíte inteira — qualquer outra
 * classe que já tenha commitado um `Usuario` no H2 compartilhado
 * (`DB_CLOSE_DELAY=-1`) faria esse teste ver `configured: true` mesmo
 * partindo de uma tabela "vazia" do ponto de vista deste método. O rollback
 * ao final de cada teste devolve a tabela ao estado anterior, sem afetar
 * nenhuma outra classe.
 */
@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class SetupApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @BeforeEach
    void limpar() {
        usuarios.deleteAll();
    }

    @Test
    void configuradoFalseQuandoNenhumUsuarioExiste() throws Exception {
        mvc.perform(get("/api/v1/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void configuradoTrueQuandoJaExisteUsuario() throws Exception {
        usuarios.saveAndFlush(new Usuario(null, "gestor1", "hash-qualquer", "GESTOR", null));

        mvc.perform(get("/api/v1/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }

    @Test
    void setupCriaOGestorEDevolveSessaoAutenticada() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/v1/setup").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"admin\", \"password\": \"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("GESTOR"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.estacaoId").doesNotExist());

        // Prova de que /setup grava sessão autenticada de verdade, não só o
        // corpo da resposta: uma segunda requisição, mesmo MockHttpSession,
        // enxerga a autenticação feita pelo POST /setup.
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("GESTOR"));
    }

    @Test
    void setupSeSegundaVezDevolve409() throws Exception {
        usuarios.saveAndFlush(new com.dev4.cypher.model.Usuario(null, "ja-existe", "hash", "GESTOR", null));

        mvc.perform(post("/api/v1/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"outro\", \"password\": \"x\"}"))
                .andExpect(status().isConflict());
    }
}
