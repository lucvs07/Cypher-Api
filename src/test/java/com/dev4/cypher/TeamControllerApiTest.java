package com.dev4.cypher;

import com.dev4.cypher.repository.EquipeRepository;
import com.dev4.cypher.repository.EstacaoRepository;
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
class TeamControllerApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EquipeRepository equipes;

    @Autowired
    private EstacaoRepository estacoes;

    private MockHttpSession gestor;

    private static final String EQUIPE = """
            {"id": "EQ-77", "nome": "Equipe A", "turno": "MANHA", "estacaoIds": ["EST-77"]}
            """;

    @BeforeEach
    void setUp() throws Exception {
        equipes.deleteAll();
        estacoes.deleteAll();
        gestor = loginGestorBootstrap(mvc);
        mvc.perform(post("/api/v1/stations").session(gestor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-77\", \"nome\": \"Esteira\", \"setor\": \"montagem\"}"));
    }

    @Test
    void cadastraEquipeEDevolve201() throws Exception {
        mvc.perform(post("/api/v1/teams").session(gestor)
                        .contentType(MediaType.APPLICATION_JSON).content(EQUIPE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("EQ-77"))
                .andExpect(jsonPath("$.turno").value("MANHA"))
                .andExpect(jsonPath("$.ativa").value(true));
    }

    @Test
    void codigoDuplicadoDevolve409() throws Exception {
        mvc.perform(post("/api/v1/teams").session(gestor)
                .contentType(MediaType.APPLICATION_JSON).content(EQUIPE));

        mvc.perform(post("/api/v1/teams").session(gestor)
                        .contentType(MediaType.APPLICATION_JSON).content(EQUIPE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value(Matchers.containsString("EQ-77")));
    }

    @Test
    void listaEquipes() throws Exception {
        mvc.perform(post("/api/v1/teams").session(gestor)
                .contentType(MediaType.APPLICATION_JSON).content(EQUIPE));

        mvc.perform(get("/api/v1/teams").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("EQ-77"));
    }

    @Test
    void editaEquipe() throws Exception {
        mvc.perform(post("/api/v1/teams").session(gestor)
                .contentType(MediaType.APPLICATION_JSON).content(EQUIPE));

        mvc.perform(put("/api/v1/teams/EQ-77").session(gestor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"EQ-77\", \"nome\": \"Equipe A - renomeada\", "
                                + "\"turno\": \"MANHA\", \"estacaoIds\": [\"EST-77\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Equipe A - renomeada"));
    }

    @Test
    void editarInexistenteDevolve404() throws Exception {
        mvc.perform(put("/api/v1/teams/EQ-99").session(gestor)
                        .contentType(MediaType.APPLICATION_JSON).content(
                                "{\"id\": \"EQ-99\", \"nome\": \"X\", \"turno\": \"MANHA\", \"estacaoIds\": [\"EST-77\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeEquipeEDevolve204() throws Exception {
        mvc.perform(post("/api/v1/teams").session(gestor)
                .contentType(MediaType.APPLICATION_JSON).content(EQUIPE));

        mvc.perform(delete("/api/v1/teams/EQ-77").session(gestor)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/teams").session(gestor))
                .andExpect(jsonPath("$[?(@.id=='EQ-77')]").isEmpty());
    }

    @Test
    void removerInexistenteDevolve404() throws Exception {
        mvc.perform(delete("/api/v1/teams/EQ-99").session(gestor)).andExpect(status().isNotFound());
    }
}
