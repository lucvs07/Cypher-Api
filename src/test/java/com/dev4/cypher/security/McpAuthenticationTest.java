package com.dev4.cypher.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpAuthenticationTest {

    @Test
    void carregaCredencialEEmpresaComRoleMcp() {
        McpAuthentication auth = new McpAuthentication(42L, "acme-test");

        assertThat(auth.getMcpCredentialId()).isEqualTo(42L);
        assertThat(auth.getEmpresaId()).isEqualTo("acme-test");
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_MCP");
        assertThat(auth.getPrincipal()).isEqualTo(42L);
        assertThat(auth.getCredentials()).isNull();
    }
}
