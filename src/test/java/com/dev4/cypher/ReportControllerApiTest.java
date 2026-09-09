package com.dev4.cypher;

import com.dev4.cypher.security.AuthTestSupport;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    void csvDePlacarEscapaNomeComVirgulaEAspas() throws Exception {
        // Spec E6 §4 -- corretude do escaping do CSV. Em vez de checar a
        // string bruta com aspas duplicadas manualmente, reparseia o CSV
        // gerado com o mesmo parser (Apache Commons CSV) e compara a celula
        // decodificada com o nome original.
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        criarEstacao(session, "EST-RPT5");
        String nomeComVirgulaEAspas = "Equipe \"A\", Turno 1";
        mvc.perform(post("/api/v1/teams").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EQ-RPT5\", \"nome\": " + jsonString(nomeComVirgulaEAspas) + ", \"turno\": \"MANHA\", "
                        + "\"estacaoIds\": [\"EST-RPT5\"], \"ativa\": true}"));

        String csv = mvc.perform(get("/api/v1/reports/placar.csv").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        CSVRecord linha = linhaPorEquipeId(csv, "EQ-RPT5");
        assertThat(linha.get("nome")).isEqualTo(nomeComVirgulaEAspas);
    }

    @Test
    void csvDeIncidentesFiltraPorSeverityMinEOrdenaPorDataHoraDesc() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        String apiKey = criarCameraEPegarApiKey(session, "CAM-RPT6", "EST-06");

        // detectedAt distintos: alem de cobrir o filtro, permite provar que o
        // CSV sai ordenado por dataHora DESC (Important #1) -- se o Sort nao
        // for de fato aplicado por Pageable.unpaged(Sort), a ordem das duas
        // linhas na leitura sem filtro fica arbitraria e o assertThat de
        // ordem abaixo falha.
        mvc.perform(post("/api/v1/incidents").header("X-Cypher-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadDeIncidente("CAM-RPT6", "incident-rpt-6-baixa", 1,
                                "2026-08-29T10:00:00Z")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/incidents").header("X-Cypher-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadDeIncidente("CAM-RPT6", "incident-rpt-6-alta", 4,
                                "2026-08-30T10:00:00Z")))
                .andExpect(status().isCreated());

        // Controle: sem severityMin, as duas linhas aparecem -- prova que a
        // ausencia da linha "baixa" no proximo passo vem do filtro, nao de a
        // ingestao ter falhado silenciosamente.
        String csvSemFiltro = mvc.perform(get("/api/v1/reports/incidents.csv")
                        .param("camera", "CAM-RPT6").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(csvSemFiltro).contains("incident-rpt-6-alta");
        assertThat(csvSemFiltro).contains("incident-rpt-6-baixa");
        // Mais recente (alta, 08-30) primeiro que o mais antigo (baixa, 08-29).
        assertThat(csvSemFiltro.indexOf("incident-rpt-6-alta"))
                .isLessThan(csvSemFiltro.indexOf("incident-rpt-6-baixa"));

        String csvFiltrado = mvc.perform(get("/api/v1/reports/incidents.csv")
                        .param("camera", "CAM-RPT6")
                        .param("severityMin", "3")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csvFiltrado).contains("incident-rpt-6-alta");
        assertThat(csvFiltrado).doesNotContain("incident-rpt-6-baixa");
    }

    @Test
    void csvDeIncidentesSanitizaReasonComPrefixoDeFormula() throws Exception {
        // Achado de seguranca da revisao final: POST /api/v1/incidents e permitAll
        // e nao sanitiza `reason`/`zoneName` -- um valor iniciado com "=" e
        // interpretado como formula por Excel/LibreOffice ao abrir o CSV.
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        String apiKey = criarCameraEPegarApiKey(session, "CAM-RPT7", "EST-07");

        mvc.perform(post("/api/v1/incidents").header("X-Cypher-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadDeIncidenteComReason("CAM-RPT7", "incident-rpt-7",
                        "=HYPERLINK(\"http://evil\",\"click\")")));

        String csv = mvc.perform(get("/api/v1/reports/incidents.csv")
                        .param("camera", "CAM-RPT7").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        CSVRecord linha = linhaPorIncidentKey(csv, "incident-rpt-7");
        assertThat(linha.get("reason")).isEqualTo("'=HYPERLINK(\"http://evil\",\"click\")");
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

    @Test
    void pdfDeResumoRepassaSetorAoHeatmap() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);
        mvc.perform(post("/api/v1/stations").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"EST-PDFA\", \"nome\": \"Estacao Pdf A\", \"setor\": \"pdf-setor-a\", \"ativa\": true}"));
        String apiKey = criarCameraEPegarApiKey(session, "CAM-PDFA", "EST-PDFA");
        mvc.perform(post("/api/v1/incidents").header("X-Cypher-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadDeIncidente("CAM-PDFA", "incident-pdf-a")));

        // payloadDeIncidente grava detectedAt fixo em 2026-08-29T10:00:00Z (ver
        // helper no fim do arquivo) -- since aqui ancora nesse dia pra nao
        // depender de o teste rodar exatamente "hoje".
        byte[] pdfDoSetorCerto = mvc.perform(get("/api/v1/reports/summary.pdf")
                        .param("since", "2026-08-29T00:00:00Z")
                        .param("setor", "pdf-setor-a")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        byte[] pdfDeOutroSetor = mvc.perform(get("/api/v1/reports/summary.pdf")
                        .param("since", "2026-08-29T00:00:00Z")
                        .param("setor", "pdf-setor-que-nao-existe")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // Com o setor errado, a contagem de EST-PDFA no heatmap do PDF zera --
        // os dois documentos deixam de ser identicos.
        assertThat(pdfDoSetorCerto).isNotEqualTo(pdfDeOutroSetor);
    }

    @Test
    void pdfDeResumoDevolve400ParaTurnoInvalido() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);

        mvc.perform(get("/api/v1/reports/summary.pdf").param("turno", "X").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void pdfDeResumoDevolve400ParaEpiInvalido() throws Exception {
        MockHttpSession session = AuthTestSupport.loginGestorBootstrap(mvc);

        mvc.perform(get("/api/v1/reports/summary.pdf").param("epi", "colete").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
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
        return payloadDeIncidente(cameraId, incidentKey, 3, "2026-08-29T10:00:00Z");
    }

    private String payloadDeIncidente(String cameraId, String incidentKey, int severity, String detectedAt) {
        return ("{\"incidentKey\": \"%s\", \"cameraId\": \"%s\", \"runId\": \"run-1\", \"trackId\": 1, "
                + "\"type\": \"FALL_RISK\", \"severity\": %d, \"reason\": \"fallen_sustained\", "
                + "\"postureCategory\": \"fallen\", \"rebaScore\": 11, \"actionLevel\": 3, "
                + "\"zoneStatus\": \"outside\", \"detectedAt\": \"%s\"}")
                .formatted(incidentKey, cameraId, severity, detectedAt);
    }

    private String payloadDeIncidenteComReason(String cameraId, String incidentKey, String reason) {
        return ("{\"incidentKey\": \"%s\", \"cameraId\": \"%s\", \"runId\": \"run-1\", \"trackId\": 1, "
                + "\"type\": \"FALL_RISK\", \"severity\": 3, \"reason\": %s, "
                + "\"postureCategory\": \"fallen\", \"rebaScore\": 11, \"actionLevel\": 3, "
                + "\"zoneStatus\": \"outside\", \"detectedAt\": \"2026-08-29T10:00:00Z\"}")
                .formatted(incidentKey, cameraId, jsonString(reason));
    }

    /** Escapa uma string Java como literal JSON, para montar payloads de teste com aspas/virgulas no valor. */
    private String jsonString(String valor) {
        return "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private CSVRecord linhaPorEquipeId(String csv, String equipeId) throws Exception {
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .build().parse(new StringReader(csv))) {
            List<CSVRecord> registros = parser.getRecords();
            return registros.stream().filter(r -> equipeId.equals(r.get("equipeId"))).findFirst()
                    .orElseThrow(() -> new AssertionError("linha nao encontrada para equipeId=" + equipeId));
        }
    }

    private CSVRecord linhaPorIncidentKey(String csv, String incidentKey) throws Exception {
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .build().parse(new StringReader(csv))) {
            List<CSVRecord> registros = parser.getRecords();
            return registros.stream().filter(r -> incidentKey.equals(r.get("incidentKey"))).findFirst()
                    .orElseThrow(() -> new AssertionError("linha nao encontrada para incidentKey=" + incidentKey));
        }
    }
}
