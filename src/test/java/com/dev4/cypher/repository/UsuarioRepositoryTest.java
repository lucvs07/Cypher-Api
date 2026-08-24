package com.dev4.cypher.repository;

import com.dev4.cypher.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UsuarioRepositoryTest {

    @Autowired
    private UsuarioRepository usuarios;

    @Test
    void encontraPeloUsername() {
        usuarios.saveAndFlush(new Usuario(null, "gestor1", "hash-qualquer"));

        assertThat(usuarios.findByUsername("gestor1")).isPresent();
        assertThat(usuarios.findByUsername("gestor1").orElseThrow().getPasswordHash())
                .isEqualTo("hash-qualquer");
        assertThat(usuarios.findByUsername("inexistente")).isEmpty();
    }

    @Test
    void existsByUsernameDistingueCadastrado() {
        usuarios.saveAndFlush(new Usuario(null, "gestor1", "hash"));

        assertThat(usuarios.existsByUsername("gestor1")).isTrue();
        assertThat(usuarios.existsByUsername("gestor2")).isFalse();
    }

    @Test
    void idEhGeradoAutomaticamente() {
        Usuario salvo = usuarios.saveAndFlush(new Usuario(null, "gestor1", "hash"));

        assertThat(salvo.getId()).isNotNull();
    }
}
