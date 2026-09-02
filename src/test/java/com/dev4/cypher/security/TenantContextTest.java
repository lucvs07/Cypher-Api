package com.dev4.cypher.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @Test
    void keyPrefixaEmpresaIdNoCodigo() {
        assertThat(TenantContext.key("acme", "EST-01")).isEqualTo("acme:EST-01");
    }

    @Test
    void codigoRemoveOPrefixoDaChave() {
        assertThat(TenantContext.codigo("acme:EST-01")).isEqualTo("EST-01");
    }

    @Test
    void codigoDevolveOValorIntactoSeNaoHouverPrefixo() {
        // Defensivo: nenhum caminho de producao chama codigo() sem prefixo,
        // mas o metodo nao deve lancar nem truncar por engano.
        assertThat(TenantContext.codigo("EST-01")).isEqualTo("EST-01");
    }

    @Test
    void codigoUsaOPrimeiroDoisPontosComoSeparador() {
        // O slug de empresa (formato ^[a-z0-9-]{2,30}$) nunca contem ':',
        // entao o primeiro ':' na chave e sempre o separador correto.
        assertThat(TenantContext.codigo("acme:EST-01:extra")).isEqualTo("EST-01:extra");
    }
}
