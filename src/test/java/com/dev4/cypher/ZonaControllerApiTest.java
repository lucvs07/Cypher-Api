package com.dev4.cypher;

import com.dev4.cypher.security.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class ZonaControllerApiTest {

    @Autowired
    private MockMvc mvc;

    private static final String POLIGONO_VALIDO =
            "[{\"x\":0.2,\"y\":0.2},{\"x\":0.6,\"y\":0.2},{\"x\":0.6,\"y\":0.6}]";

    @Test
    void cadastraListaEEditaUmaZona() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        criarCamera(session, "CAM-ZR1", "EST-01");

        mvc.perform(post("/api/v1/cameras/CAM-ZR1/zones").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"Area da prensa\", \"zoneType\": \"RESTRICTED\", "
                                + "\"polygon\": %s, \"severity\": 4, \"requiredPpe\": [\"capacete\"]}")
                                .formatted(POLIGONO_VALIDO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Area da prensa"))
                .andExpect(jsonPath("$.polygon.length()").value(3));

        String corpo = mvc.perform(get("/api/v1/cameras/CAM-ZR1/zones").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(corpo, "$[0].id");

        mvc.perform(put("/api/v1/cameras/CAM-ZR1/zones/" + id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"Area critica\", \"zoneType\": \"ATTENTION\", "
                                + "\"polygon\": %s, \"severity\": 2, \"requiredPpe\": []}")
                                .formatted(POLIGONO_VALIDO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Area critica"))
                .andExpect(jsonPath("$.zoneType").value("ATTENTION"));
    }

    @Test
    void delimitaAvaliacaoDefaultFalsoEPersisteQuandoMarcado() throws Exception {
        // Achado SPI 2026-09-16: ate entao zona so controlava severidade de
        // alerta, nunca o que era avaliado (EPI/pose sempre rodam pra todo
        // mundo). Este campo declara a zona como area de trabalho — default
        // falso preserva o comportamento anterior sem o cadastro precisar
        // saber que o campo existe.
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        criarCamera(session, "CAM-ZR9", "EST-01");

        mvc.perform(post("/api/v1/cameras/CAM-ZR9/zones").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"Area da prensa\", \"zoneType\": \"RESTRICTED\", "
                                + "\"polygon\": %s, \"severity\": 4, \"requiredPpe\": []}")
                                .formatted(POLIGONO_VALIDO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.delimitaAvaliacao").value(false));

        String corpo = mvc.perform(post("/api/v1/cameras/CAM-ZR9/zones").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"Bancada de montagem\", \"zoneType\": \"ATTENTION\", "
                                + "\"polygon\": %s, \"severity\": 1, \"requiredPpe\": [], "
                                + "\"delimitaAvaliacao\": true}")
                                .formatted(POLIGONO_VALIDO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.delimitaAvaliacao").value(true))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(corpo, "$.id");

        mvc.perform(get("/api/v1/cameras/CAM-ZR9/zones/config").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].delimitaAvaliacao").value(true));

        mvc.perform(put("/api/v1/cameras/CAM-ZR9/zones/" + id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"Bancada de montagem\", \"zoneType\": \"ATTENTION\", "
                                + "\"polygon\": %s, \"severity\": 1, \"requiredPpe\": []}")
                                .formatted(POLIGONO_VALIDO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delimitaAvaliacao").value(false));
    }

    @Test
    void poligonoPreservaValorEOrdemDosVerticesAposPostEGet() throws Exception {
        // @OrderColumn em Zona.polygon existe justamente para que recarregar
        // do banco nunca embaralhe a ordem dos vértices — este teste fixa
        // valor E índice de cada vértice, não só a contagem. Vértices 2 e 3
        // ({0.6,0.2} e {0.6,0.6}) detectam uma troca x/y que o vértice 1
        // ({0.2,0.2}, x==y) sozinho não detectaria.
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        criarCamera(session, "CAM-ZR5", "EST-01");

        mvc.perform(post("/api/v1/cameras/CAM-ZR5/zones").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"Area da prensa\", \"zoneType\": \"RESTRICTED\", "
                                + "\"polygon\": %s, \"severity\": 4, \"requiredPpe\": [\"capacete\"]}")
                                .formatted(POLIGONO_VALIDO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.polygon[0].x").value(0.2))
                .andExpect(jsonPath("$.polygon[0].y").value(0.2))
                .andExpect(jsonPath("$.polygon[1].x").value(0.6))
                .andExpect(jsonPath("$.polygon[1].y").value(0.2))
                .andExpect(jsonPath("$.polygon[2].x").value(0.6))
                .andExpect(jsonPath("$.polygon[2].y").value(0.6));

        mvc.perform(get("/api/v1/cameras/CAM-ZR5/zones").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].polygon[0].x").value(0.2))
                .andExpect(jsonPath("$[0].polygon[0].y").value(0.2))
                .andExpect(jsonPath("$[0].polygon[1].x").value(0.6))
                .andExpect(jsonPath("$[0].polygon[1].y").value(0.2))
                .andExpect(jsonPath("$[0].polygon[2].x").value(0.6))
                .andExpect(jsonPath("$[0].polygon[2].y").value(0.6));
    }

    @Test
    void putMudaQuantidadeDeVerticesEPersisteAposReGet() throws Exception {
        // @OrderColumn sobre @ElementCollection é o clássico ponto de
        // Hibernate que quebra ao crescer/encolher a coleção — este teste
        // troca um polígono de 3 vértices para um de 4 (e re-lê do banco)
        // para provar que o novo tamanho e os novos valores persistem
        // corretamente, não só que a contagem bate na resposta do PUT.
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        criarCamera(session, "CAM-ZR6", "EST-01");

        String corpo = mvc.perform(post("/api/v1/cameras/CAM-ZR6/zones").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"Area da prensa\", \"zoneType\": \"RESTRICTED\", "
                                + "\"polygon\": %s, \"severity\": 4, \"requiredPpe\": []}")
                                .formatted(POLIGONO_VALIDO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.polygon.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(corpo, "$.id");

        String poligonoQuatroVertices =
                "[{\"x\":0.1,\"y\":0.1},{\"x\":0.9,\"y\":0.1},{\"x\":0.9,\"y\":0.9},{\"x\":0.1,\"y\":0.9}]";
        mvc.perform(put("/api/v1/cameras/CAM-ZR6/zones/" + id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"Area critica\", \"zoneType\": \"ATTENTION\", "
                                + "\"polygon\": %s, \"severity\": 2, \"requiredPpe\": []}")
                                .formatted(poligonoQuatroVertices)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.polygon.length()").value(4));

        mvc.perform(get("/api/v1/cameras/CAM-ZR6/zones").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].polygon.length()").value(4))
                .andExpect(jsonPath("$[0].polygon[0].x").value(0.1))
                .andExpect(jsonPath("$[0].polygon[0].y").value(0.1))
                .andExpect(jsonPath("$[0].polygon[1].x").value(0.9))
                .andExpect(jsonPath("$[0].polygon[1].y").value(0.1))
                .andExpect(jsonPath("$[0].polygon[2].x").value(0.9))
                .andExpect(jsonPath("$[0].polygon[2].y").value(0.9))
                .andExpect(jsonPath("$[0].polygon[3].x").value(0.1))
                .andExpect(jsonPath("$[0].polygon[3].y").value(0.9));
    }

    @Test
    void coordenadaForaDeFaixaDevolve400() throws Exception {
        // ZoneRequest/PontoRequest validam cada vértice via
        // @DecimalMin("0.0")/@DecimalMax("1.0") cascateando por
        // List<@Valid PontoRequest> — este teste fixa que um valor
        // "estilo pixel" leftover (700, fora de [0,1]) é rejeitado com 400.
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        criarCamera(session, "CAM-ZR7", "EST-01");

        mvc.perform(post("/api/v1/cameras/CAM-ZR7/zones").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"x\", \"zoneType\": \"RESTRICTED\", "
                                + "\"polygon\": [{\"x\":700,\"y\":300},{\"x\":0.2,\"y\":0.2},{\"x\":0.6,\"y\":0.6}], "
                                + "\"severity\": 2, \"requiredPpe\": []}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void poligonoComMenosDeTresVerticesDevolve400() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        criarCamera(session, "CAM-ZR2", "EST-01");

        mvc.perform(post("/api/v1/cameras/CAM-ZR2/zones").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"x\", \"zoneType\": \"RESTRICTED\", "
                                + "\"polygon\": [{\"x\":0.1,\"y\":0.1},{\"x\":0.2,\"y\":0.2}], "
                                + "\"severity\": 2, \"requiredPpe\": []}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void severityForaDeFaixaDevolve400() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        criarCamera(session, "CAM-ZR3", "EST-01");

        mvc.perform(post("/api/v1/cameras/CAM-ZR3/zones").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"x\", \"zoneType\": \"RESTRICTED\", "
                                + "\"polygon\": %s, \"severity\": 5, \"requiredPpe\": []}")
                                .formatted(POLIGONO_VALIDO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cameraInexistenteDevolve404() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);

        mvc.perform(post("/api/v1/cameras/CAM-NAO-EXISTE/zones").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"x\", \"zoneType\": \"RESTRICTED\", "
                                + "\"polygon\": %s, \"severity\": 2, \"requiredPpe\": []}")
                                .formatted(POLIGONO_VALIDO)))
                .andExpect(status().isNotFound());
    }

    @Test
    void desativarEhSoftDeleteEDesapareceDaListagem() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        criarCamera(session, "CAM-ZR4", "EST-01");
        String corpo = mvc.perform(post("/api/v1/cameras/CAM-ZR4/zones").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"x\", \"zoneType\": \"RESTRICTED\", "
                                + "\"polygon\": %s, \"severity\": 2, \"requiredPpe\": []}")
                                .formatted(POLIGONO_VALIDO)))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(corpo, "$.id");

        mvc.perform(delete("/api/v1/cameras/CAM-ZR4/zones/" + id).session(session))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/cameras/CAM-ZR4/zones").session(session))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void zonaDeCameraDeUmaEmpresaNaoApareceOuEditaPorOutra() throws Exception {
        MockHttpSession sessaoAcme = setupEmpresa("acme-zr", "Acme");
        MockHttpSession sessaoGlobex = setupEmpresa("globex-zr", "Globex");
        criarCamera(sessaoAcme, "CAM-01", "EST-01");
        criarCamera(sessaoGlobex, "CAM-01", "EST-01");

        String corpo = mvc.perform(post("/api/v1/cameras/CAM-01/zones").session(sessaoAcme)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"Da Acme\", \"zoneType\": \"RESTRICTED\", "
                                + "\"polygon\": %s, \"severity\": 2, \"requiredPpe\": []}")
                                .formatted(POLIGONO_VALIDO)))
                .andReturn().getResponse().getContentAsString();
        Number idDaAcme = JsonPath.read(corpo, "$.id");

        mvc.perform(get("/api/v1/cameras/CAM-01/zones").session(sessaoGlobex))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/api/v1/cameras/CAM-01/zones/" + idDaAcme).session(sessaoGlobex)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"roubada\", \"zoneType\": \"RESTRICTED\", "
                                + "\"polygon\": %s, \"severity\": 2, \"requiredPpe\": []}")
                                .formatted(POLIGONO_VALIDO)))
                .andExpect(status().isNotFound());
    }

    private void criarCamera(MockHttpSession session, String codigo, String estacaoId) throws Exception {
        mvc.perform(post("/api/v1/stations").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"" + estacaoId + "\", \"nome\": \"Estacao\", \"setor\": \"x\", \"ativa\": true}"));
        mvc.perform(post("/api/v1/cameras").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"id\": \"%s\", \"nome\": \"Camera\", \"estacaoId\": \"%s\", "
                                + "\"streamUrl\": \"http://127.0.0.1:9000/stream\", \"ativa\": true}")
                                .formatted(codigo, estacaoId)));
    }

    @Test
    void configSoDevolveZonasAtivasEAceitaChaveDePipeline() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        criarCamera(session, "CAM-CFG1", "EST-01");
        String corpoAtiva = mvc.perform(post("/api/v1/cameras/CAM-CFG1/zones").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"Ativa\", \"zoneType\": \"RESTRICTED\", "
                                + "\"polygon\": %s, \"severity\": 2, \"requiredPpe\": []}")
                                .formatted(POLIGONO_VALIDO)))
                .andReturn().getResponse().getContentAsString();
        String corpoInativa = mvc.perform(post("/api/v1/cameras/CAM-CFG1/zones").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"nome\": \"Inativa\", \"zoneType\": \"RESTRICTED\", "
                                + "\"polygon\": %s, \"severity\": 2, \"requiredPpe\": []}")
                                .formatted(POLIGONO_VALIDO)))
                .andReturn().getResponse().getContentAsString();
        Number idInativa = JsonPath.read(corpoInativa, "$.id");
        mvc.perform(delete("/api/v1/cameras/CAM-CFG1/zones/" + idInativa).session(session));

        // Acesso GESTOR (sessão).
        mvc.perform(get("/api/v1/cameras/CAM-CFG1/zones/config").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Ativa"));

        // Acesso por X-Cypher-Key da propria camera, sem sessao.
        String apiKey = apiKeyDeCamera(session, "CAM-CFG1");
        mvc.perform(get("/api/v1/cameras/CAM-CFG1/zones/config").header("X-Cypher-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    private String apiKeyDeCamera(MockHttpSession session, String codigo) throws Exception {
        String corpo = mvc.perform(post("/api/v1/cameras/" + codigo + "/apikey/regenerate").session(session))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.apiKey");
    }

    private MockHttpSession setupEmpresa(String empresaId, String empresaNome) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/v1/setup").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"username\": \"gestor\", \"password\": \"senha123\", "
                                + "\"empresaId\": \"%s\", \"empresaNome\": \"%s\"}").formatted(empresaId, empresaNome)))
                .andExpect(status().isOk());
        return session;
    }
}
