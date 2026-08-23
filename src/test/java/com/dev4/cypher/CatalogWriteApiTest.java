package com.dev4.cypher;

import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.repository.EstacaoRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class CatalogWriteApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EstacaoRepository estacoes;

    @Autowired
    private CameraRepository cameras;

    private static final String ESTACAO = """
            {"id": "EST-77", "nome": "Esteira de montagem", "setor": "montagem"}
            """;

    private static final String CAMERA = """
            {
              "id": "CAM-77",
              "nome": "Frontal",
              "estacaoId": "EST-77",
              "streamUrl": "http://127.0.0.1:8177/stream",
              "fonte": "rtsp://${CAM77_USER}:${CAM77_PASS}@10.0.0.7/stream"
            }
            """;

    @BeforeEach
    void limpar() {
        cameras.deleteAll();
        estacoes.deleteAll();
    }

    @Test
    void cadastraEstacaoEDevolve201() throws Exception {
        mvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON).content(ESTACAO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("EST-77"))
                .andExpect(jsonPath("$.ativa").value(true));
    }

    @Test
    void recusaCodigoComFormatoInvalido() throws Exception {
        mvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"est_77\", \"nome\": \"X\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void codigoDuplicadoDevolve409ComMensagem() throws Exception {
        mvc.perform(post("/api/v1/stations")
                .contentType(MediaType.APPLICATION_JSON).content(ESTACAO));

        mvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON).content(ESTACAO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value(Matchers.containsString("EST-77")));
    }

    @Test
    void editaEstacao() throws Exception {
        mvc.perform(post("/api/v1/stations")
                .contentType(MediaType.APPLICATION_JSON).content(ESTACAO));

        mvc.perform(put("/api/v1/stations/EST-77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"EST-77\", \"nome\": \"Esteira 1\", \"setor\": \"final\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Esteira 1"));
    }

    @Test
    void renomearCodigoDevolve409() throws Exception {
        mvc.perform(post("/api/v1/stations")
                .contentType(MediaType.APPLICATION_JSON).content(ESTACAO));

        mvc.perform(put("/api/v1/stations/EST-77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"EST-88\", \"nome\": \"Esteira\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void editarEstacaoInexistenteDevolve404() throws Exception {
        mvc.perform(put("/api/v1/stations/EST-99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"EST-99\", \"nome\": \"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void desativaEstacaoEDevolve204() throws Exception {
        mvc.perform(post("/api/v1/stations")
                .contentType(MediaType.APPLICATION_JSON).content(ESTACAO));

        mvc.perform(delete("/api/v1/stations/EST-77")).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/stations"))
                .andExpect(jsonPath("$[?(@.id=='EST-77')]").isEmpty());
        mvc.perform(get("/api/v1/stations").param("incluirInativas", "true"))
                .andExpect(jsonPath("$[?(@.id=='EST-77')].ativa").value(false));
    }

    @Test
    void cadastraCameraEDevolve201ComFonte() throws Exception {
        mvc.perform(post("/api/v1/stations")
                .contentType(MediaType.APPLICATION_JSON).content(ESTACAO));

        mvc.perform(post("/api/v1/cameras")
                        .contentType(MediaType.APPLICATION_JSON).content(CAMERA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("CAM-77"))
                .andExpect(jsonPath("$.fonte").value(Matchers.containsString("CAM77_USER")));
    }

    @Test
    void cameraDeEstacaoInexistenteDevolve422() throws Exception {
        mvc.perform(post("/api/v1/cameras")
                        .contentType(MediaType.APPLICATION_JSON).content(CAMERA))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value(Matchers.containsString("EST-77")));
    }

    @Test
    void itemIndividualTrazFonteMasAListaNao() throws Exception {
        mvc.perform(post("/api/v1/stations")
                .contentType(MediaType.APPLICATION_JSON).content(ESTACAO));
        mvc.perform(post("/api/v1/cameras")
                .contentType(MediaType.APPLICATION_JSON).content(CAMERA));

        mvc.perform(get("/api/v1/cameras/CAM-77"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fonte").exists());

        mvc.perform(get("/api/v1/cameras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fonte").doesNotExist());
    }

    @Test
    void cameraDesativadaSaiDaListaMasContinuaAcessivelPorId() throws Exception {
        mvc.perform(post("/api/v1/stations")
                .contentType(MediaType.APPLICATION_JSON).content(ESTACAO));
        mvc.perform(post("/api/v1/cameras")
                .contentType(MediaType.APPLICATION_JSON).content(CAMERA));

        mvc.perform(delete("/api/v1/cameras/CAM-77")).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/cameras"))
                .andExpect(jsonPath("$[?(@.id=='CAM-77')]").isEmpty());
        mvc.perform(get("/api/v1/cameras/CAM-77"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativa").value(false));
    }

    @Test
    void cameraInexistenteDevolve404() throws Exception {
        mvc.perform(get("/api/v1/cameras/CAM-99")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/cameras/CAM-99")).andExpect(status().isNotFound());
    }

    @Test
    void listaDeCamerasIncluiInativasSoComOptIn() throws Exception {
        mvc.perform(post("/api/v1/stations")
                .contentType(MediaType.APPLICATION_JSON).content(ESTACAO));
        mvc.perform(post("/api/v1/cameras")
                .contentType(MediaType.APPLICATION_JSON).content(CAMERA));

        mvc.perform(delete("/api/v1/cameras/CAM-77")).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/cameras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='CAM-77')]").isEmpty());
        mvc.perform(get("/api/v1/cameras").param("incluirInativas", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='CAM-77')]").isEmpty());
        mvc.perform(get("/api/v1/cameras").param("incluirInativas", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='CAM-77')]").isNotEmpty());
    }
}
