package com.dev4.cypher.service;

import com.dev4.cypher.dto.McpCredentialCriadaResponse;
import com.dev4.cypher.dto.McpCredentialRequest;
import com.dev4.cypher.repository.McpCredentialRepository;
import com.dev4.cypher.security.GestorAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class McpCredentialServiceTest {

    @Autowired
    private McpCredentialService service;
    @Autowired
    private McpCredentialRepository repository;
    @Autowired
    private PasswordEncoder encoder;

    @BeforeEach
    void autenticarComoGestor() {
        SecurityContextHolder.getContext().setAuthentication(
                new GestorAuthentication("gestor@acme-test", "acme-test"));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void criarGeraApiKeyNoFormatoIdPontoSecretEHashEhValido() {
        McpCredentialCriadaResponse resposta = service.criar(new McpCredentialRequest("Claude Desktop"));

        assertThat(resposta.apiKey()).startsWith(resposta.id() + ".");
        String secretPlano = resposta.apiKey().substring((resposta.id() + ".").length());

        String hashPersistido = repository.findById(resposta.id()).orElseThrow().getSecretHash();
        assertThat(encoder.matches(secretPlano, hashPersistido)).isTrue();
    }

    @Test
    void revogarMarcaComoInativaEIdDeOutraEmpresaEhTratadoComoInexistente() {
        McpCredentialCriadaResponse criada = service.criar(new McpCredentialRequest("Agente de demo"));

        assertThat(service.revogar(criada.id())).isTrue();
        assertThat(repository.findById(criada.id()).orElseThrow().isAtiva()).isFalse();
    }

    @Test
    void criarComNomeDuplicadoNaMesmaEmpresaLancaConflito() {
        service.criar(new McpCredentialRequest("Duplicada"));

        assertThatThrownBy(() -> service.criar(new McpCredentialRequest("Duplicada")))
                .isInstanceOf(com.dev4.cypher.catalog.CatalogValidationException.class);
    }
}
