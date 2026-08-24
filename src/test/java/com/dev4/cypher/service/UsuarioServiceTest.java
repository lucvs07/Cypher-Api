package com.dev4.cypher.service;

import com.dev4.cypher.catalog.CatalogValidationException;
import com.dev4.cypher.dto.UserRequest;
import com.dev4.cypher.dto.UsuarioResponse;
import com.dev4.cypher.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UsuarioServiceTest {

    @Autowired
    private UsuarioRepository usuarios;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private UsuarioService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(usuarios, encoder);
    }

    private UserRequest gestor2() {
        return new UserRequest("gestor2", "GESTOR", "senha123");
    }

    @Test
    void cadastraUsuarioComSenhaHasheada() {
        UsuarioResponse criado = service.criar(gestor2());

        assertThat(criado.username()).isEqualTo("gestor2");
        assertThat(criado.papel()).isEqualTo("GESTOR");
        String hash = usuarios.findByUsername("gestor2").orElseThrow().getPasswordHash();
        assertThat(hash).isNotEqualTo("senha123");
        assertThat(encoder.matches("senha123", hash)).isTrue();
    }

    @Test
    void recusaPapelDiferenteDeGestor() {
        assertThatThrownBy(() -> service.criar(new UserRequest("op1", "OPERADOR", "senha123")))
                .isInstanceOf(CatalogValidationException.class)
                .satisfies(e -> assertThat(((CatalogValidationException) e).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void recusaUsernameDuplicado() {
        service.criar(gestor2());

        assertThatThrownBy(() -> service.criar(gestor2()))
                .isInstanceOf(CatalogValidationException.class)
                .satisfies(e -> assertThat(((CatalogValidationException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void recusaCadastroSemSenha() {
        assertThatThrownBy(() -> service.criar(new UserRequest("gestor2", "GESTOR", null)))
                .isInstanceOf(CatalogValidationException.class)
                .satisfies(e -> assertThat(((CatalogValidationException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void editaUsernameSemRedefinirSenha() {
        UsuarioResponse criado = service.criar(gestor2());
        String hashOriginal = usuarios.findById(criado.id()).orElseThrow().getPasswordHash();

        Optional<UsuarioResponse> editado = service.atualizar(criado.id(),
                new UserRequest("gestor2-renomeado", "GESTOR", null));

        assertThat(editado).isPresent();
        assertThat(editado.get().username()).isEqualTo("gestor2-renomeado");
        assertThat(usuarios.findById(criado.id()).orElseThrow().getPasswordHash()).isEqualTo(hashOriginal);
    }

    @Test
    void editaRedefinindoSenha() {
        UsuarioResponse criado = service.criar(gestor2());
        String hashOriginal = usuarios.findById(criado.id()).orElseThrow().getPasswordHash();

        service.atualizar(criado.id(), new UserRequest("gestor2", "GESTOR", "novaSenha456"));

        String hashNovo = usuarios.findById(criado.id()).orElseThrow().getPasswordHash();
        assertThat(hashNovo).isNotEqualTo(hashOriginal);
        assertThat(encoder.matches("novaSenha456", hashNovo)).isTrue();
    }

    @Test
    void editarInexistenteDevolveVazio() {
        assertThat(service.atualizar(999L, new UserRequest("x", "GESTOR", null))).isEmpty();
    }

    @Test
    void removeUsuario() {
        UsuarioResponse criado = service.criar(gestor2());

        assertThat(service.remover(criado.id())).isTrue();
        assertThat(usuarios.findById(criado.id())).isEmpty();
    }

    @Test
    void removerInexistenteDevolveFalso() {
        assertThat(service.remover(999L)).isFalse();
    }
}
