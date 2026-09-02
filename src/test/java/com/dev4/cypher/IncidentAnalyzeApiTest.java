package com.dev4.cypher;

import com.dev4.cypher.repository.AlertaRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.OffsetDateTime;

import static com.dev4.cypher.security.AuthTestSupport.loginGestorBootstrap;
import static com.dev4.cypher.security.AuthTestSupport.loginOperador;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class IncidentAnalyzeApiTest {

    private static HttpServer servidor;

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1/messages", troca -> {
            String corpo = "{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"situacao\\\":\\\"ok\\\","
                    + "\\\"epis_identificados\\\":[],\\\"epis_ausentes\\\":[],"
                    + "\\\"risco_identificado\\\":false,\\\"tipo_risco\\\":null,"
                    + "\\\"nivel_risco\\\":\\\"baixo\\\",\\\"justificativa\\\":\\\"ok\\\","
                    + "\\\"acao_recomendada\\\":\\\"nenhuma\\\"}\"}]}";
            byte[] resposta = corpo.getBytes();
            troca.sendResponseHeaders(200, resposta.length);
            troca.getResponseBody().write(resposta);
            troca.close();
        });
        servidor.start();

        registry.add("cypher.llm.provider", () -> "claude");
        registry.add("cypher.llm.claude.base-url",
                () -> "http://127.0.0.1:" + servidor.getAddress().getPort());
        registry.add("cypher.llm.claude.api-key", () -> "chave-de-teste");
        registry.add("cypher.llm.claude.model", () -> "claude-teste");
    }

    @AfterAll
    static void derrubarServidor() {
        servidor.stop(0);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AlertaRepository alertas;

    @BeforeEach
    void limpar() {
        alertas.deleteAll();
    }

    private Long criarAlertaComSnapshot(String snapshotUrl) {
        var alerta = new com.dev4.cypher.model.Alerta();
        alerta.setIncidentKey("k-" + System.nanoTime());
        alerta.setCameraId("CAM-01");
        alerta.setRunId("run-analyze");
        alerta.setTrackId(1);
        alerta.setTipo(com.dev4.cypher.model.AlertaTipo.EPI_VIOLATION);
        alerta.setSeveridade(2);
        alerta.setNivel(com.dev4.cypher.model.AlertaNivel.fromSeverity(2));
        alerta.setReason("r");
        alerta.setDescricao("Fixture de teste de analise");
        alerta.setPosturaCategoria("adequate");
        alerta.setEpiPresent(java.util.List.of());
        alerta.setEpiMissing(java.util.List.of("luvas"));
        alerta.setRebaScore(3);
        alerta.setActionLevel(1);
        alerta.setZoneStatus("outside");
        alerta.setSnapshotUrl(snapshotUrl);
        alerta.setDataHora(OffsetDateTime.now());
        alerta.setStatus(com.dev4.cypher.model.AlertaStatus.PENDENTE);
        return alertas.save(alerta).getId();
    }

    @Test
    void gestorAnalisaComSucesso() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);
        Long id = criarAlertaComSnapshot("https://exemplo.com/foto.jpg");

        mvc.perform(post("/api/v1/incidents/" + id + "/analyze").session(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.situacao").value("ok"));
    }

    @Test
    void operadorRecebe403() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);
        Long id = criarAlertaComSnapshot("https://exemplo.com/foto.jpg");
        mvc.perform(post("/api/v1/stations").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-98\", \"nome\": \"A\", \"setor\": \"s\"}"));
        mvc.perform(post("/api/v1/cameras").session(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"CAM-98\", \"nome\": \"C\", \"estacaoId\": \"EST-98\", "
                        + "\"streamUrl\": \"http://127.0.0.1:9/stream\", \"fonte\": \"f\"}"));
        String token = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/v1/stations/EST-98/token/regenerate").session(gestor))
                        .andReturn().getResponse().getContentAsString(),
                "$.token");
        MockHttpSession operador = loginOperador(mvc, token);

        mvc.perform(post("/api/v1/incidents/" + id + "/analyze").session(operador))
                .andExpect(status().isForbidden());
    }

    @Test
    void incidenteInexistenteRetorna404() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);

        mvc.perform(post("/api/v1/incidents/999999/analyze").session(gestor))
                .andExpect(status().isNotFound());
    }

    @Test
    void semSnapshotRetorna422() throws Exception {
        MockHttpSession gestor = loginGestorBootstrap(mvc);
        Long id = criarAlertaComSnapshot(null);

        mvc.perform(post("/api/v1/incidents/" + id + "/analyze").session(gestor))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").exists());
    }
}
