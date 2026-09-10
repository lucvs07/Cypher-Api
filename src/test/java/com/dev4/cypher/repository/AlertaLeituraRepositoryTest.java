package com.dev4.cypher.repository;

import com.dev4.cypher.model.AlertaLeitura;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AlertaLeituraRepositoryTest {

    @Autowired
    private AlertaLeituraRepository leituras;

    private AlertaLeitura leitura(String empresaId, String usuario, Long alertaId) {
        AlertaLeitura l = new AlertaLeitura();
        l.setEmpresaId(empresaId);
        l.setUsuario(usuario);
        l.setAlertaId(alertaId);
        l.setLidoEm(OffsetDateTime.parse("2026-09-07T10:00:00Z"));
        return l;
    }

    @Test
    void existsByEmpresaIdAndUsuarioAndAlertaIdReflecteAPresencaDaLinha() {
        assertThat(leituras.existsByEmpresaIdAndUsuarioAndAlertaId("demo", "admin@demo", 1L)).isFalse();

        leituras.saveAndFlush(leitura("demo", "admin@demo", 1L));

        assertThat(leituras.existsByEmpresaIdAndUsuarioAndAlertaId("demo", "admin@demo", 1L)).isTrue();
        // Outro usuário na mesma empresa não compartilha a leitura.
        assertThat(leituras.existsByEmpresaIdAndUsuarioAndAlertaId("demo", "outro@demo", 1L)).isFalse();
    }

    @Test
    void findByEmpresaIdAndUsuarioAndAlertaIdInFiltraSoOsIdsPedidos() {
        leituras.saveAndFlush(leitura("demo", "admin@demo", 1L));
        leituras.saveAndFlush(leitura("demo", "admin@demo", 2L));
        leituras.saveAndFlush(leitura("demo", "admin@demo", 3L));

        List<AlertaLeitura> lidas = leituras.findByEmpresaIdAndUsuarioAndAlertaIdIn(
                "demo", "admin@demo", List.of(1L, 3L, 99L));

        assertThat(lidas).extracting(AlertaLeitura::getAlertaId).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void constraintUnicaImpedeDuasLinhasParaOMesmoParUsuarioAlerta() {
        leituras.saveAndFlush(leitura("demo", "admin@demo", 1L));

        assertThatThrownBy(() -> leituras.saveAndFlush(leitura("demo", "admin@demo", 1L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
