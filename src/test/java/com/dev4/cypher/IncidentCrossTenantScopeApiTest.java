package com.dev4.cypher;

import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.model.AlertaNivel;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.model.AlertaTipo;
import com.dev4.cypher.repository.AlertaRepository;
import com.dev4.cypher.repository.EstacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static com.dev4.cypher.security.AuthTestSupport.loginGestorBootstrap;
import static com.dev4.cypher.security.AuthTestSupport.loginOperador;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Achado da revisão da Tarefa 5, corrigido na Tarefa 6:
 * {@code IncidentService.findById}/{@code changeStatus} faziam
 * {@code repository.findById(id)} SEM filtro de {@code empresaId} —
 * {@code IncidentController} só barra por {@code foraDoEscopoDoOperador},
 * que devolve {@code false} incondicionalmente para GESTOR, e que, para
 * OPERADOR, re-prefixa {@code cameraId} do incidente com o
 * {@code empresaId} DO OPERADOR em vez de usar o tenant real do próprio
 * incidente. Como códigos puros de câmera colidem entre tenants (dois
 * tenants podem ter, cada um, uma {@code CAM-91}), um OPERADOR de um tenant
 * cuja estação possua uma {@code CAM-91} conseguia passar no filtro contra
 * o incidente de {@code CAM-91} de OUTRO tenant.
 *
 * <p>A correção escopa a própria busca por {@code empresaId} — uma vez que
 * {@code findById}/{@code changeStatus} nunca resolvem um id de outro
 * tenant, nenhum dos dois caminhos (GESTOR ou OPERADOR) chega a ver o
 * incidente estrangeiro; o controller já mapeia {@code Optional.empty()}
 * para 404.
 */
@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class IncidentCrossTenantScopeApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AlertaRepository alertas;

    @Autowired
    private EstacaoRepository estacoes;

    private MockHttpSession gestorAcme;
    private MockHttpSession operadorAcmeEst91;
    private Long idIncidenteGlobex;

    @BeforeEach
    void setUp() throws Exception {
        gestorAcme = loginGestorBootstrap(mvc);

        mvc.perform(post("/api/v1/stations").session(gestorAcme).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"ESTCT-91\", \"nome\": \"A\", \"setor\": \"s\"}"));
        mvc.perform(post("/api/v1/cameras").session(gestorAcme).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"CAMCT-91\", \"nome\": \"C1\", \"estacaoId\": \"ESTCT-91\", "
                        + "\"streamUrl\": \"http://127.0.0.1:8391/stream\", \"fonte\": \"f\"}"));

        String token = estacoes.findById("acme-test:ESTCT-91").orElseThrow().getToken();
        operadorAcmeEst91 = loginOperador(mvc, token);

        // Incidente de OUTRO tenant ("globex"), com cameraId de código PURO
        // colidindo de propósito com a câmera acima de "acme-test"
        // (CAMCT-91) — prova que o OPERADOR de acme-test não passa a
        // enxergá-lo só porque a própria estação tem uma câmera de mesmo
        // código. Inserido direto via repositório: nenhuma sessão/chave de
        // "globex" existe neste teste, de propósito — a correção é escopada
        // no service, não depende de uma sessão do tenant estrangeiro.
        idIncidenteGlobex = alertas.saveAndFlush(incidenteDireto("globex:foreign-1",
                "globex:CAMCT-91", "globex")).getId();
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
    void gestorRecebe404AoLerIncidenteDeOutraEmpresa() throws Exception {
        mvc.perform(get("/api/v1/incidents/{id}", idIncidenteGlobex).session(gestorAcme))
                .andExpect(status().isNotFound());
    }

    @Test
    void gestorRecebe404AoMudarStatusDeIncidenteDeOutraEmpresa() throws Exception {
        mvc.perform(put("/api/v1/incidents/{id}/status", idIncidenteGlobex).session(gestorAcme)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"REVISADO\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void gestorRecebe404AoAnalisarIncidenteDeOutraEmpresa() throws Exception {
        mvc.perform(post("/api/v1/incidents/{id}/analyze", idIncidenteGlobex).session(gestorAcme))
                .andExpect(status().isNotFound());
    }

    @Test
    void operadorComCameraDeCodigoColidenteRecebe404AoLerIncidenteDeOutraEmpresa() throws Exception {
        mvc.perform(get("/api/v1/incidents/{id}", idIncidenteGlobex).session(operadorAcmeEst91))
                .andExpect(status().isNotFound());
    }

    @Test
    void operadorComCameraDeCodigoColidenteRecebe404AoMudarStatusDeIncidenteDeOutraEmpresa() throws Exception {
        mvc.perform(put("/api/v1/incidents/{id}/status", idIncidenteGlobex).session(operadorAcmeEst91)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"REVISADO\"}"))
                .andExpect(status().isNotFound());
    }
}
