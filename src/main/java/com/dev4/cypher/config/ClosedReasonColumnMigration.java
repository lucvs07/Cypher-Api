package com.dev4.cypher.config;

import com.dev4.cypher.model.ClosedReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Amplia o ENUM nativo de {@code alertas.closed_reason} no H2 quando
 * {@link ClosedReason} ganha valor novo.
 *
 * <p>O Hibernate cria a coluna como {@code enum ('ESCALATED', ...)} com os
 * valores do momento da criação, e o {@code ddl-auto: update} nunca altera o
 * tipo de coluna existente: sem isto, um banco criado antes de
 * {@code RENOTIFIED} (ex.: o da empresa demo) recusa o valor e o PATCH de
 * fechamento do pipeline falha. Os valores que já existem na coluna são
 * preservados (união, nunca troca), e a migração é idempotente — com a
 * coluna já contendo todos os valores do enum, nada acontece.
 */
@Component
public class ClosedReasonColumnMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClosedReasonColumnMigration.class);

    private final JdbcTemplate jdbc;

    public ClosedReasonColumnMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrar();
    }

    /** @return true quando ampliou a coluna nesta chamada. */
    public boolean migrar() {
        List<String> atuais = jdbc.queryForList("""
                SELECT e.VALUE_NAME
                FROM INFORMATION_SCHEMA.COLUMNS c
                JOIN INFORMATION_SCHEMA.ENUM_VALUES e
                  ON e.OBJECT_SCHEMA = c.TABLE_SCHEMA
                 AND e.OBJECT_NAME = c.TABLE_NAME
                 AND e.ENUM_IDENTIFIER = c.DTD_IDENTIFIER
                WHERE UPPER(c.TABLE_NAME) = 'ALERTAS'
                  AND UPPER(c.COLUMN_NAME) = 'CLOSED_REASON'
                ORDER BY e.VALUE_ORDINAL
                """, String.class);
        if (atuais.isEmpty()) {
            // Coluna inexistente ou nao-ENUM (outro banco): nada a ampliar.
            return false;
        }

        Set<String> alvo = new LinkedHashSet<>(atuais);
        Arrays.stream(ClosedReason.values()).map(Enum::name).forEach(alvo::add);
        if (alvo.size() == atuais.size()) {
            return false;
        }

        String valores = alvo.stream().map(v -> "'" + v + "'").collect(Collectors.joining(", "));
        jdbc.execute("ALTER TABLE alertas ALTER COLUMN closed_reason SET DATA TYPE ENUM(" + valores + ")");
        log.info("alertas.closed_reason ampliada para ENUM({})", valores);
        return true;
    }
}
