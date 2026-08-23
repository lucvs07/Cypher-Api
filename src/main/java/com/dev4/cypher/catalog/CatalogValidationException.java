package com.dev4.cypher.catalog;

import org.springframework.http.HttpStatus;

/**
 * Erro de negócio do cadastro, carregando o status que o cliente deve ver.
 *
 * <p>Existe para que o serviço decida o significado (duplicado é 409, estação
 * inexistente é 422) sem que o controller precise reinterpretar mensagens.
 */
public class CatalogValidationException extends RuntimeException {

    private final HttpStatus status;

    public CatalogValidationException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
