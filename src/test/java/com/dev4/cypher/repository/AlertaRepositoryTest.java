package com.dev4.cypher.repository;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import com.dev4.cypher.model.EpiTipo;

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
        a.setEpiPresent(List.of(EpiTipo.HELMET, EpiTipo.VEST));
        a.setEpiMissing(List.of());
        a.setDataHora(OffsetDateTime.of(2026, 8, 10, 14, 31, 2, 0, ZoneOffset.ofHours(-3)));
        a.setStatus(AlertaStatus.PENDENTE);
        a.setEmpresaId("demo");
        return a;
    }

    @Test
    void persisteQuedaForaDeQualquerZona() {
        Alerta salvo = repository.save(quedaForaDeZona());

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.getZona()).isNull();
        assertThat(salvo.getEpiPresent()).containsExactly(EpiTipo.HELMET, EpiTipo.VEST);
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

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    @Test
    void epiGravadoNoFormatoLegadoELidoComoEpiTipoESobreviveARegravacao() {
        // Linhas de antes do contrato EpiTipo (2026-09-22): classe crua do
        // detector, rotulo no_* e item que o modelo nao detecta.
        Alerta salvo = repository.saveAndFlush(quedaForaDeZona());
        jdbc.update("delete from alerta_epi_present where alerta_id = ?", salvo.getId());
        jdbc.update("insert into alerta_epi_present (alerta_id, epi) values (?, 'Goggles'), (?, 'boots')",
                salvo.getId(), salvo.getId());
        jdbc.update("insert into alerta_epi_missing (alerta_id, epi) values (?, 'no_helmet'), (?, 'auricular')",
                salvo.getId(), salvo.getId());
        em.clear();

        Alerta lido = repository.findById(salvo.getId()).orElseThrow();
        assertThat(lido.getEpiPresent()).containsExactly(EpiTipo.GOGGLES, EpiTipo.BOOTS);
        assertThat(lido.getEpiMissing()).containsExactly(EpiTipo.HELMET);

        // Mudar outro campo nao pode quebrar por causa do item legado...
        lido.setStatus(AlertaStatus.REVISADO);
        repository.saveAndFlush(lido);
        em.clear();
        assertThat(repository.findById(salvo.getId()).orElseThrow().getEpiMissing())
                .containsExactly(EpiTipo.HELMET);

        // ...e regravar a lista normaliza o banco pro nome canonico.
        Alerta deNovo = repository.findById(salvo.getId()).orElseThrow();
        deNovo.setEpiMissing(deNovo.getEpiMissing());
        repository.saveAndFlush(deNovo);
        assertThat(jdbc.queryForList("select epi from alerta_epi_missing where alerta_id = ?",
                String.class, salvo.getId())).containsExactly("HELMET");
    }
}
