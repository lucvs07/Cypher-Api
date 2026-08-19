package com.dev4.cypher.repository;

import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.model.AlertaNivel;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.model.AlertaTipo;
import com.dev4.cypher.model.ClosedReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AlertaRepositoryTest {

    @Autowired
    private AlertaRepository repository;

    private Alerta quedaForaDeZona() {
        Alerta a = new Alerta();
        a.setIncidentKey("CAM-01:run-a:track-7:20260810T143102482Z");
        a.setRunId("run-a");
        a.setTrackId(7);
        a.setCameraId("CAM-01");
        a.setTipo(AlertaTipo.FALL_RISK);
        a.setSeveridade(4);
        a.setNivel(AlertaNivel.fromSeverity(4));
        a.setDescricao("Queda detectada na camera CAM-01");
        a.setReason("fallen_sustained");
        a.setPosturaCategoria("immediate_risk");
        a.setRebaScore(11);
        a.setActionLevel(4);
        a.setZona(null);
        a.setZoneStatus("outside");
        a.setEpiPresent(List.of("HELMET", "VEST"));
        a.setEpiMissing(List.of());
        a.setDataHora(OffsetDateTime.of(2026, 8, 10, 14, 31, 2, 0, ZoneOffset.ofHours(-3)));
        a.setStatus(AlertaStatus.PENDENTE);
        return a;
    }

    @Test
    void persisteQuedaForaDeQualquerZona() {
        Alerta salvo = repository.save(quedaForaDeZona());

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.getZona()).isNull();
        assertThat(salvo.getEpiPresent()).containsExactly("HELMET", "VEST");
    }

    @Test
    void preservaOffsetDoTimestamp() {
        repository.saveAndFlush(quedaForaDeZona());

        Alerta lido = repository.findAll().get(0);
        assertThat(lido.getDataHora().toInstant())
                .isEqualTo(OffsetDateTime.of(2026, 8, 10, 14, 31, 2, 0, ZoneOffset.ofHours(-3)).toInstant());
    }

    @Test
    void encontraPorIncidentKey() {
        repository.saveAndFlush(quedaForaDeZona());

        Optional<Alerta> achado = repository.findByIncidentKey(
                "CAM-01:run-a:track-7:20260810T143102482Z");

        assertThat(achado).isPresent();
        assertThat(achado.get().getTipo()).isEqualTo(AlertaTipo.FALL_RISK);
    }

    @Test
    void rejeitaIncidentKeyDuplicada() {
        repository.saveAndFlush(quedaForaDeZona());

        assertThatThrownBy(() -> repository.saveAndFlush(quedaForaDeZona()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void guardaDadosDeFechamento() {
        Alerta a = repository.saveAndFlush(quedaForaDeZona());

        a.setEncerradoEm(a.getDataHora().plusSeconds(7));
        a.setDuracaoSegundos(6.6);
        a.setSeveridadePico(4);
        a.setClosedReason(ClosedReason.STATE_EXIT);
        repository.saveAndFlush(a);

        Alerta lido = repository.findById(a.getId()).orElseThrow();
        assertThat(lido.getDuracaoSegundos()).isEqualTo(6.6);
        assertThat(lido.getClosedReason()).isEqualTo(ClosedReason.STATE_EXIT);
    }
}
