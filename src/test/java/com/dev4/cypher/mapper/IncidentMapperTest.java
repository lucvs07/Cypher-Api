package com.dev4.cypher.mapper;

import com.dev4.cypher.dto.IncidentRequest;
import com.dev4.cypher.dto.IncidentResponse;
import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.model.AlertaNivel;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.model.AlertaTipo;
import com.dev4.cypher.security.GestorAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentMapperTest {

    private final IncidentMapper mapper = new IncidentMapper();

    @BeforeEach
    void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(
                new GestorAuthentication("gestor@demo", "demo"));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private IncidentRequest queda() {
        return new IncidentRequest(
                "CAM-01:run-a:track-7:20260810T143102482Z",
                "CAM-01",
                "run-a",
                7,
                AlertaTipo.FALL_RISK,
                4,
                "fallen_sustained",
                "immediate_risk",
                11,
                4,
                null,
                "outside",
                List.of("HELMET", "VEST"),
                List.of(),
                OffsetDateTime.of(2026, 8, 10, 14, 31, 2, 0, ZoneOffset.ofHours(-3)),
                null);
    }

    @Test
    void derivaNivelStatusEDescricaoNaEntrada() {
        Alerta alerta = mapper.toEntity(queda());

        assertThat(alerta.getNivel()).isEqualTo(AlertaNivel.ALTO);
        assertThat(alerta.getStatus()).isEqualTo(AlertaStatus.PENDENTE);
        assertThat(alerta.getSeveridade()).isEqualTo(4);
        assertThat(alerta.getDescricao()).contains("Queda").contains("CAM-01");
    }

    @Test
    void naoConfiaEmIdNemStatusVindosDoCliente() {
        Alerta alerta = mapper.toEntity(queda());

        assertThat(alerta.getId()).isNull();
        assertThat(alerta.getStatus()).isEqualTo(AlertaStatus.PENDENTE);
    }

    @Test
    void listasNulasViramListasVazias() {
        IncidentRequest semEpi = new IncidentRequest(
                "k", "CAM-01", "run-a", 1, AlertaTipo.ERGONOMIC_RISK, 2,
                "reba_9", "ergonomically_inadequate", 9, 3, null, "outside",
                null, null,
                OffsetDateTime.of(2026, 8, 10, 10, 0, 0, 0, ZoneOffset.UTC), null);

        Alerta alerta = mapper.toEntity(semEpi);

        assertThat(alerta.getEpiPresent()).isEmpty();
        assertThat(alerta.getEpiMissing()).isEmpty();
    }

    @Test
    void converteEntidadeParaRespostaPreservandoSeveridadeCrua() {
        Alerta alerta = mapper.toEntity(queda());
        alerta.setId(42L);

        IncidentResponse response = mapper.toResponse(alerta);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.incidentKey()).isEqualTo("CAM-01:run-a:track-7:20260810T143102482Z");
        assertThat(response.severity()).isEqualTo(4);
        assertThat(response.nivel()).isEqualTo(AlertaNivel.ALTO);
        assertThat(response.zoneName()).isNull();
    }

    @Test
    void descricaoDescreveCadaTipoDeIncidente() {
        assertThat(mapper.descricaoDe(AlertaTipo.ERGONOMIC_RISK, "reba_11", "CAM-02"))
                .contains("ergonom");
        assertThat(mapper.descricaoDe(AlertaTipo.ZONE_VIOLATION, "zone", "CAM-02"))
                .contains("zona");
        assertThat(mapper.descricaoDe(AlertaTipo.EPI_VIOLATION, "no_helmet", "CAM-02"))
                .contains("EPI");
    }
}
