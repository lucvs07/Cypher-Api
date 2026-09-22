package com.dev4.cypher.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Banco criado antes de RENOTIFIED tem {@code closed_reason} como ENUM nativo
 * do H2 com só os 4 valores antigos. H2 isolado por teste, fora do contexto
 * Spring, para o ALTER nunca vazar para o schema compartilhado da suíte.
 */
class ClosedReasonColumnMigrationTest {

    private JdbcTemplate jdbc;
    private ClosedReasonColumnMigration migration;

    @BeforeEach
    void schemaAntigo() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:closed-reason-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE alertas (id BIGINT PRIMARY KEY, closed_reason "
                + "ENUM('ESCALATED','PIPELINE_STOPPED','STATE_EXIT','TRACK_LOST'))");
        jdbc.update("INSERT INTO alertas VALUES (1, 'STATE_EXIT')");
        migration = new ClosedReasonColumnMigration(jdbc);
    }

    @Test
    void schemaAntigoRecusaRenotified() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO alertas VALUES (2, 'RENOTIFIED')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ampliaEnumPreservandoValoresEPassaAAceitarRenotified() {
        assertThat(migration.migrar()).isTrue();

        assertThat(jdbc.queryForObject("SELECT closed_reason FROM alertas WHERE id = 1", String.class))
                .isEqualTo("STATE_EXIT");
        jdbc.update("INSERT INTO alertas VALUES (2, 'RENOTIFIED')");
        assertThat(jdbc.queryForObject("SELECT closed_reason FROM alertas WHERE id = 2", String.class))
                .isEqualTo("RENOTIFIED");
    }

    @Test
    void idempotente() {
        assertThat(migration.migrar()).isTrue();
        assertThat(migration.migrar()).isFalse();
    }

    @Test
    void semTabelaNaoFazNada() {
        jdbc.execute("DROP TABLE alertas");
        assertThat(migration.migrar()).isFalse();
    }
}
