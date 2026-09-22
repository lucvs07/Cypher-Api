package com.dev4.cypher.mcp;

import com.dev4.cypher.dto.RiskAnalysisResponse;
import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.model.AlertaNivel;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.model.AlertaTipo;
import com.dev4.cypher.model.AnalysisStatus;
import com.dev4.cypher.model.IncidentLlmAnalysis;
import com.dev4.cypher.repository.AlertaRepository;
import com.dev4.cypher.repository.IncidentLlmAnalysisRepository;
import com.dev4.cypher.security.McpAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static com.dev4.cypher.security.AuthTestSupport.loginGestorBootstrap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Achado CRITICAL da revisao final de branch (feat/mcp-server): {@code
 * LlmAnalysisService} (usado por {@code get_incident_analysis}/{@code
 * analyze_incident}) NAO e escopado por tenant — usa {@code
 * alertas.findById}/{@code analyses.findFirstByAlertaIdOrderByCriadoEmDesc}
 * sem filtrar por empresaId. Diferente do {@code IncidentController} (REST),
 * que barra a mesma operacao checando {@code IncidentService.findById}
 * (escopado) antes de chamar o LLM, as tools MCP pulavam essa checagem: uma
 * credencial MCP da empresa A conseguia ler/disparar analise de risco sobre
 * incidentes de qualquer outra empresa so sabendo o id.
 *
 * <p>Mesmo padrao de duas "empresas" simuladas de {@code
 * IncidentCrossTenantScopeApiTest}/{@code MultiTenantIsolationApiTest}: uma
 * credencial MCP real e criada para "acme-test" (via a API real, com sessao
 * de GESTOR), e o incidente-alvo pertence a "globex", inserido direto via
 * repositorio (nenhuma sessao/credencial de "globex" existe neste teste, de
 * proposito).
 *
 * <p>O bean {@link IncidentMcpTools} e chamado diretamente (nao via HTTP
 * {@code /mcp}): a fiacao real do protocolo MCP/JSON-RPC e coberta por
 * {@code McpSecurityLockdownApiTest}; aqui o que importa e o {@link
 * com.dev4.cypher.security.TenantContext}, lido do
 * {@code SecurityContextHolder} exatamente como {@code
 * McpKeyAuthenticationFilter} o deixaria numa requisicao real — por isso a
 * {@link McpAuthentication} usada carrega o id de uma credencial de fato
 * criada pela API, com o empresaId real da sessao GESTOR que a criou.
 */
@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class IncidentMcpToolsCrossTenantScopeApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private IncidentMcpTools tools;

    @Autowired
    private AlertaRepository alertas;

    @Autowired
    private IncidentLlmAnalysisRepository analises;

    private Long idIncidenteGlobex;
    private Long idIncidenteAcme;

    @BeforeEach
    void setUp() throws Exception {
        MockHttpSession gestorAcme = loginGestorBootstrap(mvc);

        MvcResult criacao = mvc.perform(post("/api/v1/mcp-credentials").session(gestorAcme)
                        .contentType("application/json")
                        .content("{\"nome\":\"Teste Cross-Tenant\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String apiKey = com.jayway.jsonpath.JsonPath.read(
                criacao.getResponse().getContentAsString(), "$.apiKey");
        Long mcpCredentialId = Long.parseLong(apiKey.split("\\.", 2)[0]);

        // Incidente de OUTRO tenant ("globex") com uma analise ja calculada —
        // sem essa analise pre-existente, get_incident_analysis retornaria
        // vazio de qualquer forma (nunca foi analisado), o que não provaria
        // nada sobre o isolamento.
        idIncidenteGlobex = alertas.saveAndFlush(
                incidenteDireto("globex:foreign-1", "globex:CAM-91", "globex")).getId();
        analises.saveAndFlush(new IncidentLlmAnalysis(idIncidenteGlobex, "gemini", "v1",
                AnalysisStatus.OK, "{\"situacao\":\"dado da globex\",\"nivel_risco\":\"alto\"}",
                null, OffsetDateTime.now()));

        // Incidente do PROPRIO tenant ("acme-test") com analise, para provar
        // que a guarda nao bloqueia acesso legitimo (controle positivo).
        idIncidenteAcme = alertas.saveAndFlush(
                incidenteDireto("acme-test:own-1", "acme-test:CAM-01", "acme-test")).getId();
        analises.saveAndFlush(new IncidentLlmAnalysis(idIncidenteAcme, "gemini", "v1",
                AnalysisStatus.OK, "{\"situacao\":\"dado da acme\",\"nivel_risco\":\"baixo\"}",
                null, OffsetDateTime.now()));

        SecurityContextHolder.getContext().setAuthentication(
                new McpAuthentication(mcpCredentialId, "acme-test"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Alerta incidenteDireto(String incidentKey, String cameraId, String empresaId) {
        Alerta a = new Alerta();
        a.setIncidentKey(incidentKey);
        a.setCameraId(cameraId);
        a.setRunId("run-ct");
        a.setTrackId(1);
        a.setTipo(AlertaTipo.FALL_RISK);
        a.setSeveridade(4);
        a.setNivel(AlertaNivel.fromSeverity(4));
        a.setReason("r");
        a.setPosturaCategoria("immediate_risk");
        a.setRebaScore(11);
        a.setActionLevel(4);
        a.setZoneStatus("outside");
        a.setDescricao("Fixture direto de outro tenant (%s)".formatted(cameraId));
        a.setDataHora(OffsetDateTime.parse("2026-08-10T14:31:02.482-03:00"));
        a.setStatus(AlertaStatus.PENDENTE);
        a.setEmpresaId(empresaId);
        return a;
    }

    @Test
    void getIncidentAnalysisNaoVazaAnaliseDeIncidenteDeOutraEmpresa() {
        Optional<RiskAnalysisResponse> resultado = tools.getIncidentAnalysis(idIncidenteGlobex);

        assertThat(resultado).isEmpty();
    }

    @Test
    void analyzeIncidentNaoVazaNemAcionaAnaliseSobreIncidenteDeOutraEmpresa() {
        assertThatThrownBy(() -> tools.analyzeIncident(idIncidenteGlobex))
                .isInstanceOf(NoSuchElementException.class);
    }

    /** Controle positivo: a mesma guarda nao bloqueia acesso a um incidente do proprio tenant. */
    @Test
    void getIncidentAnalysisFuncionaNormalmenteParaIncidenteDoProprioTenant() {
        Optional<RiskAnalysisResponse> resultado = tools.getIncidentAnalysis(idIncidenteAcme);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().situacao()).isEqualTo("dado da acme");
    }
}
