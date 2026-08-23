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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class CameraConfigApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EstacaoRepository estacoes;

    @Autowired
    private CameraRepository cameras;

    @BeforeEach
    void cadastrar() throws Exception {
        cameras.deleteAll();
        estacoes.deleteAll();

        mvc.perform(post("/api/v1/stations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-55\", \"nome\": \"Prensa\", \"setor\": \"estamparia\"}"));

        mvc.perform(post("/api/v1/cameras")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "id": "CAM-55",
                          "nome": "Prensa - lateral",
                          "estacaoId": "EST-55",
                          "streamUrl": "http://127.0.0.1:8155/stream",
                          "fonte": "rtsp://${CAM55_USER}:${CAM55_PASS}@10.0.0.9/stream"
                        }
                        """));
    }

    @Test
    void devolveFonteComPlaceholderEPortaDerivadaDaUrl() throws Exception {
        mvc.perform(get("/api/v1/cameras/CAM-55/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("CAM-55"))
                .andExpect(jsonPath("$.estacaoId").value("EST-55"))
                .andExpect(jsonPath("$.streamPort").value(8155))
                .andExpect(jsonPath("$.ativa").value(true))
                .andExpect(jsonPath("$.fonte").value(
                        "rtsp://${CAM55_USER}:${CAM55_PASS}@10.0.0.9/stream"));
    }

    @Test
    void naoDevolveStreamUrlBruta() throws Exception {
        // O pipeline precisa da porta para servir, não da URL que o browser usa.
        mvc.perform(get("/api/v1/cameras/CAM-55/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streamUrl").doesNotExist());
    }

    @Test
    void cameraDesativadaDevolve409() throws Exception {
        mvc.perform(delete("/api/v1/cameras/CAM-55")).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/cameras/CAM-55/config"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value(Matchers.containsString("CAM-55")));
    }

    @Test
    void cameraInexistenteDevolve404() throws Exception {
        mvc.perform(get("/api/v1/cameras/CAM-99/config"))
                .andExpect(status().isNotFound());
    }

    @Test
    void estacaoDesativadaDevolve409MesmoComCameraAtiva() throws Exception {
        // A camera continua ativa; e a estacao que sai de operacao.
        mvc.perform(delete("/api/v1/stations/EST-55")).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/cameras/CAM-55/config"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value(Matchers.containsString("EST-55")));
    }
}
