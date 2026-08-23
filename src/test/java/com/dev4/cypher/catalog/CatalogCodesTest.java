package com.dev4.cypher.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogCodesTest {

    @ParameterizedTest
    @ValueSource(strings = {"EST-01", "CAM-01", "GALPAO1-A", "AB-1", "ABCDEFGHIJ-12345678"})
    void aceitaCodigosBemFormados(String codigo) {
        assertThat(CatalogCodes.valido(codigo)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "est-01",        // minúscula
            "EST01",         // sem hífen
            "EST-",          // sufixo vazio
            "-01",           // prefixo vazio
            "E-1",           // prefixo curto demais
            "EST_01",        // separador errado
            "EST-01-A",      // dois hífens
            "ABCDEFGHIJK-1", // prefixo longo demais
            "EST-123456789", // sufixo longo demais
            "EST 01",        // espaço
    })
    void recusaCodigosMalFormados(String codigo) {
        assertThat(CatalogCodes.valido(codigo)).isFalse();
    }

    @Test
    void recusaCodigoNulo() {
        assertThat(CatalogCodes.valido(null)).isFalse();
    }

    @Test
    void extraiPortaExplicitaDaUrlDeStream() {
        assertThat(CatalogCodes.portaDe("http://127.0.0.1:8090/stream")).isEqualTo(8090);
        assertThat(CatalogCodes.portaDe("http://camera.local:9000/mjpeg")).isEqualTo(9000);
    }

    @Test
    void exigePortaExplicita() {
        assertThatThrownBy(() -> CatalogCodes.portaDe("http://127.0.0.1/stream"))
                .isInstanceOf(CatalogValidationException.class)
                .hasMessageContaining("porta");
    }

    @Test
    void recusaUrlIlegivel() {
        assertThatThrownBy(() -> CatalogCodes.portaDe("nao e uma url"))
                .isInstanceOf(CatalogValidationException.class);
    }

    @Test
    void erroDeValidacaoCarregaOStatusQueOClienteDeveVer() {
        CatalogValidationException erro =
                new CatalogValidationException(HttpStatus.CONFLICT, "duplicado");

        assertThat(erro.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(erro.getMessage()).isEqualTo("duplicado");
    }
}
