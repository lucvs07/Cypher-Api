package com.dev4.cypher.service;

import com.dev4.cypher.catalog.CatalogValidationException;
import com.dev4.cypher.dto.UserRequest;
import com.dev4.cypher.dto.UsuarioResponse;
import com.dev4.cypher.model.Estacao;
import com.dev4.cypher.repository.EstacaoRepository;
import com.dev4.cypher.repository.UsuarioRepository;
import com.dev4.cypher.security.GestorAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UsuarioServiceTest {

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private EstacaoRepository estacoes;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private UsuarioService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(usuarios, estacoes, encoder);
        SecurityContextHolder.getContext().setAuthentication(
                new GestorAuthentication("gestor@demo", "demo"));
        estacoes.saveAndFlush(new Estacao("demo:EST-01", "Estacao 1", "montagem", true, "tok-1", "demo"));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private UserRequest gestor2() {
        return new UserRequest("gestor2", "GESTOR", "senha123", null);
    }

    private UserRequest operador1() {
        return new UserRequest("op1", "OPERADOR", "senha123", "EST-01");
    }

    @Test
    void cadastraUsuarioComSenhaHasheada() {
        UsuarioResponse criado = service.criar(gestor2());

        assertThat(criado.username()).isEqualTo("gestor2@demo");
        assertThat(criado.papel()).isEqualTo("GESTOR");
        assertThat(criado.estacaoId()).isNull();
        String hash = usuarios.findByUsername("gestor2@demo").orElseThrow().getPasswordHash();
        assertThat(hash).isNotEqualTo("senha123");
        assertThat(encoder.matches("senha123", hash)).isTrue();
    }

    @Test
    void cadastraOperadorVinculadoAUmaEstacaoValida() {
        UsuarioResponse criado = service.criar(operador1());

        assertThat(criado.username()).isEqualTo("op1@demo");
        assertThat(criado.papel()).isEqualTo("OPERADOR");
        assertThat(criado.estacaoId()).isEqualTo("EST-01");
    }

    @Test
    void recusaPapelInvalido() {
        assertThatThrownBy(() -> service.criar(new UserRequest("x", "SUPERVISOR", "senha123", null)))
                .isInstanceOf(CatalogValidationException.class)
                .satisfies(e -> assertThat(((CatalogValidationException) e).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void recusaOperadorSemEstacaoId() {
        assertThatThrownBy(() -> service.criar(new UserRequest("op1", "OPERADOR", "senha123", null)))
                .isInstanceOf(CatalogValidationException.class)
                .satisfies(e -> assertThat(((CatalogValidationException) e).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void recusaOperadorComEstacaoInexistente() {
        assertThatThrownBy(() -> service.criar(new UserRequest("op1", "OPERADOR", "senha123", "EST-99")))
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
        assertThatThrownBy(() -> service.criar(new UserRequest("gestor2", "GESTOR", null, null)))
                .isInstanceOf(CatalogValidationException.class)
                .satisfies(e -> assertThat(((CatalogValidationException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void editaUsernameSemRedefinirSenha() {
        service.criar(gestor2());
        String hashOriginal = usuarios.findByUsername("gestor2@demo").orElseThrow().getPasswordHash();

        Optional<UsuarioResponse> editado = service.atualizar("gestor2@demo",
                new UserRequest("gestor2-renomeado@demo", "GESTOR", null, null));

        assertThat(editado).isPresent();
        assertThat(editado.get().username()).isEqualTo("gestor2-renomeado@demo");
        assertThat(usuarios.findByUsername("gestor2-renomeado@demo").orElseThrow().getPasswordHash())
                .isEqualTo(hashOriginal);
    }

    @Test
    void editaUsernameSemSufixoDoTenantDevolve422() {
        service.criar(gestor2());

        assertThatThrownBy(() -> service.atualizar("gestor2@demo",
                new UserRequest("gestor2-renomeado", "GESTOR", null, null)))
                .isInstanceOf(CatalogValidationException.class)
                .satisfies(e -> assertThat(((CatalogValidationException) e).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void editaTrocandoDeGestorParaOperadorExigeEstacao() {
        service.criar(gestor2());

        Optional<UsuarioResponse> editado = service.atualizar("gestor2@demo",
                new UserRequest("gestor2@demo", "OPERADOR", null, "EST-01"));

        assertThat(editado).isPresent();
        assertThat(editado.get().papel()).isEqualTo("OPERADOR");
        assertThat(editado.get().estacaoId()).isEqualTo("EST-01");
    }

    @Test
    void editaRedefinindoSenha() {
        service.criar(gestor2());
        String hashOriginal = usuarios.findByUsername("gestor2@demo").orElseThrow().getPasswordHash();

        service.atualizar("gestor2@demo", new UserRequest("gestor2@demo", "GESTOR", "novaSenha456", null));

        String hashNovo = usuarios.findByUsername("gestor2@demo").orElseThrow().getPasswordHash();
        assertThat(hashNovo).isNotEqualTo(hashOriginal);
        assertThat(encoder.matches("novaSenha456", hashNovo)).isTrue();
    }

    @Test
    void editarInexistenteDevolveVazio() {
        assertThat(service.atualizar("nao-existe@demo", new UserRequest("x@demo", "GESTOR", null, null))).isEmpty();
    }

    @Test
    void removeUsuario() {
        service.criar(gestor2());

        assertThat(service.remover("gestor2@demo")).isTrue();
        assertThat(usuarios.findByUsername("gestor2@demo")).isEmpty();
    }

    @Test
    void removerInexistenteDevolveFalso() {
        assertThat(service.remover("nao-existe")).isFalse();
    }
}
