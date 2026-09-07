package com.dev4.cypher;

import com.dev4.cypher.security.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class ReportControllerApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void csvDeIncidentesTemCabecalhoMesmoSemDado() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);

        String csv = mvc.perform(get("/api/v1/reports/incidents.csv")
                        .param("camera", "CAM-QUE-NAO-EXISTE-NESTE-TESTE").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).startsWith("id,incidentKey,cameraId,type,severity,nivel,reason,zoneName,detectedAt,endedAt,durationSeconds,status");
        // So o cabecalho: nenhuma linha de dado para uma camera que nao existe.
        assertThat(csv.trim().split("\r\n")).hasSize(1);
    }

    @Test
    void csvDeIncidentesContemUmaLinhaPorIncidenteDaCamera() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        String apiKey = criarCameraEPegarApiKey(session, "CAM-RPT1", "EST-01");

        mvc.perform(post("/api/v1/incidents").header("X-Cypher-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadDeIncidente("CAM-RPT1", "incident-rpt-1")));

        String csv = mvc.perform(get("/api/v1/reports/incidents.csv")
                        .param("camera", "CAM-RPT1").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv.trim().split("\r\n")).hasSize(2); // cabecalho + 1 linha
        assertThat(csv).contains("CAM-RPT1");
    }

    @Test
    void csvDePlacarTemCabecalhoEUmaLinhaPorEquipe() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        criarEstacao(session, "EST-RPT1");
        mvc.perform(post("/api/v1/teams").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EQ-RPT1\", \"nome\": \"Equipe Rpt\", \"turno\": \"MANHA\", "
                        + "\"estacaoIds\": [\"EST-RPT1\"], \"ativa\": true}"));

        String csv = mvc.perform(get("/api/v1/reports/placar.csv").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).startsWith(
                "equipeId,nome,turno,dadosSuficientes,conformidade,cobertura,sequenciaAtual,melhorSequencia,deltaConformidadeSemana");
        assertThat(csv).contains("Equipe Rpt");
    }

    @Test
    void csvDePlacarNaoQuebraQuandoEquipeTemDadosInsuficientes() throws Exception {
        // Equipe recem-criada, sem MonitoramentoDiario: cobertura 0 < piso 0,5 (E5 spec SS3.2)
        // -> dadosSuficientes=false, conformidade/deltaConformidadeSemana nulos. So confirma
        // que o CSV nao quebra e imprime a celula vazia, sem exigir dado real de heartbeat.
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        criarEstacao(session, "EST-RPT2");
        mvc.perform(post("/api/v1/teams").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EQ-RPT2\", \"nome\": \"Equipe Sem Dado\", \"turno\": \"MANHA\", "
                        + "\"estacaoIds\": [\"EST-RPT2\"], \"ativa\": true}"));

        String csv = mvc.perform(get("/api/v1/reports/placar.csv").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("EQ-RPT2,Equipe Sem Dado,MANHA,false,,");
    }

    @Test
    void pdfDeResumoComecaComAAssinaturaDeBytesPdf() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);

        byte[] pdf = mvc.perform(get("/api/v1/reports/summary.pdf").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void pdfDeResumoNaoQuebraComEquipeDeDadosInsuficientes() throws Exception {
        // Regressao do achado da revisao de design (2026-09-08): conformidade() nula
        // (equipe sem cobertura suficiente) desempacotava e quebrava a geracao do PDF.
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        criarEstacao(session, "EST-RPT3");
        mvc.perform(post("/api/v1/teams").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EQ-RPT3\", \"nome\": \"Equipe Sem Dado Pdf\", \"turno\": \"MANHA\", "
                        + "\"estacaoIds\": [\"EST-RPT3\"], \"ativa\": true}"));

        byte[] pdf = mvc.perform(get("/api/v1/reports/summary.pdf").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void pdfDeResumoEscapaCaracteresHtmlDeNomeDeEquipe() throws Exception {
        // Achado de revisao (task 2, fix round 1): nome de equipe/estacao ia
        // direto para a tabela HTML sem escaping -- "<", ">" e "&" corrompiam
        // a estrutura da tabela silenciosamente (sem excecao). Confirma que o
        // PDF continua valido e que o texto do nome sobrevive intacto (ou seja,
        // foi escapado na fonte e decodificado de volta na extracao de texto).
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        criarEstacao(session, "EST-RPT4");
        mvc.perform(post("/api/v1/teams").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EQ-RPT4\", \"nome\": \"Equipe <Testes> & Cia\", \"turno\": \"MANHA\", "
                        + "\"estacaoIds\": [\"EST-RPT4\"], \"ativa\": true}"));

        byte[] pdf = mvc.perform(get("/api/v1/reports/summary.pdf").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        try (org.apache.pdfbox.pdmodel.PDDocument documento =
                     org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
            String texto = new org.apache.pdfbox.text.PDFTextStripper().getText(documento);
            assertThat(texto).contains("Equipe <Testes> & Cia");
        }
    }

    private void criarEstacao(MockHttpSession session, String estacaoId) throws Exception {
        mvc.perform(post("/api/v1/stations").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"" + estacaoId + "\", \"nome\": \"E\", \"setor\": \"x\", \"ativa\": true}"));
    }

    private String criarCameraEPegarApiKey(MockHttpSession session, String codigo, String estacaoId) throws Exception {
        mvc.perform(post("/api/v1/stations").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"" + estacaoId + "\", \"nome\": \"E\", \"setor\": \"x\", \"ativa\": true}"));
        mvc.perform(post("/api/v1/cameras").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"id\": \"%s\", \"nome\": \"C\", \"estacaoId\": \"%s\", "
                        + "\"streamUrl\": \"http://127.0.0.1:9200/stream\", \"ativa\": true}")
                        .formatted(codigo, estacaoId)));
        String corpo = mvc.perform(post("/api/v1/cameras/" + codigo + "/apikey/regenerate").session(session))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(corpo, "$.apiKey");
    }

    private String payloadDeIncidente(String cameraId, String incidentKey) {
        return ("{\"incidentKey\": \"%s\", \"cameraId\": \"%s\", \"runId\": \"run-1\", \"trackId\": 1, "
                + "\"type\": \"FALL_RISK\", \"severity\": 3, \"reason\": \"fallen_sustained\", "
                + "\"postureCategory\": \"fallen\", \"rebaScore\": 11, \"actionLevel\": 3, "
                + "\"zoneStatus\": \"outside\", \"detectedAt\": \"2026-08-29T10:00:00Z\"}")
                .formatted(incidentKey, cameraId);
    }
}
