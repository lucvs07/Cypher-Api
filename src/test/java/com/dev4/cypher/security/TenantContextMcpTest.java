package com.dev4.cypher.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextMcpTest {

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveEmpresaIdAPartirDeMcpAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(new McpAuthentication(7L, "acme-test"));

        assertThat(TenantContext.currentEmpresaId()).isEqualTo("acme-test");
    }
}
