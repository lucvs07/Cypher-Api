package com.dev4.cypher;

import com.dev4.cypher.repository.EmpresaRepository;
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
 * `@Transactional` (mesmo padrão de `CatalogWriteApiTest`): rollback ao final
 * de cada teste devolve o banco ao estado anterior, sem afetar nenhuma outra
 * classe. O conflito de sign-up agora é por slug de empresa, então os testes
 * não dependem mais de a tabela `usuarios` estar vazia.
 */
@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class SetupApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EmpresaRepository empresas;

    @Test
    void setupCriaOGestorEDevolveSessaoAutenticada() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/v1/setup").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"admin\", \"password\": \"admin123\", "
                                + "\"empresaId\": \"acme\", \"empresaNome\": \"Acme\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("GESTOR"))
                .andExpect(jsonPath("$.username").value("admin@acme"))
                .andExpect(jsonPath("$.empresaId").value("acme"))
                .andExpect(jsonPath("$.empresaNome").value("Acme"))
                .andExpect(jsonPath("$.estacaoId").doesNotExist());

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("GESTOR"));
    }

    @Test
    void setupSeSegundaVezComMesmoSlugDevolve409() throws Exception {
        empresas.saveAndFlush(new com.dev4.cypher.model.Empresa("ja-existe", "Ja Existe Ltda", true));

        mvc.perform(post("/api/v1/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"outro\", \"password\": \"x\", "
                                + "\"empresaId\": \"ja-existe\", \"empresaNome\": \"Ja Existe\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void setupComSlugDiferenteEmParaleloNaoColide() throws Exception {
        mvc.perform(post("/api/v1/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"gestor\", \"password\": \"x\", "
                                + "\"empresaId\": \"empresa-a\", \"empresaNome\": \"Empresa A\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"gestor\", \"password\": \"x\", "
                                + "\"empresaId\": \"empresa-b\", \"empresaNome\": \"Empresa B\"}"))
                .andExpect(status().isOk());
    }
}
