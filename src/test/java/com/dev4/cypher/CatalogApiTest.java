package com.dev4.cypher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "cypher.catalog.stations[0].id=EST-01",
        "cypher.catalog.stations[0].nome=Esteira de montagem",
        "cypher.catalog.stations[0].setor=montagem",
        "cypher.catalog.cameras[0].id=CAM-01",
        "cypher.catalog.cameras[0].nome=Frontal",
        "cypher.catalog.cameras[0].estacaoId=EST-01",
        "cypher.catalog.cameras[0].streamUrl=http://127.0.0.1:8090/stream",
        "cypher.catalog.cameras[0].fonte=rtsp://user:senha@cam/stream",
})
class CatalogApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void listaCamerasComUrlDeStream() throws Exception {
        mvc.perform(get("/api/v1/cameras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("CAM-01"))
                .andExpect(jsonPath("$[0].estacaoId").value("EST-01"))
                .andExpect(jsonPath("$[0].streamUrl").value("http://127.0.0.1:8090/stream"));
    }

    @Test
    void naoExpoeAFonteQuePodeConterCredencial() throws Exception {
        mvc.perform(get("/api/v1/cameras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fonte").doesNotExist());
    }

    @Test
    void listaEstacoesComSuasCameras() throws Exception {
        mvc.perform(get("/api/v1/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("EST-01"))
                .andExpect(jsonPath("$[0].setor").value("montagem"))
                .andExpect(jsonPath("$[0].cameras[0].id").value("CAM-01"));
    }
}
