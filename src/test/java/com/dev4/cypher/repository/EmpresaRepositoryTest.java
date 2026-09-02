package com.dev4.cypher.repository;

import com.dev4.cypher.model.Empresa;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class EmpresaRepositoryTest {

    @Autowired
    private EmpresaRepository empresas;

    @Test
    void gravaELeEmpresaPeloSlug() {
        empresas.saveAndFlush(new Empresa("acme", "Acme Industrial Ltda", true));

        Empresa lida = empresas.findById("acme").orElseThrow();
        assertThat(lida.getNome()).isEqualTo("Acme Industrial Ltda");
        assertThat(lida.isAtiva()).isTrue();
    }

    @Test
    void existsByIdDistingueSlugsDiferentes() {
        empresas.saveAndFlush(new Empresa("acme", "Acme", true));

        assertThat(empresas.existsById("acme")).isTrue();
        assertThat(empresas.existsById("outra")).isFalse();
    }
}
