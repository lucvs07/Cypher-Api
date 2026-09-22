package com.dev4.cypher.repository;

import com.dev4.cypher.model.McpCredential;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class McpCredentialRepositoryTest {

    @Autowired
    private McpCredentialRepository repository;

    @Test
    void persisteERecuperaCredencial() {
        McpCredential nova = new McpCredential(null, "Claude Desktop", "$2a$hash", true,
                "acme-test", OffsetDateTime.now(), null);

        McpCredential salva = repository.saveAndFlush(nova);

        assertThat(salva.getId()).isNotNull();
        assertThat(repository.findById(salva.getId())).isPresent();
    }
}
