package com.dev4.cypher.controller;

import com.dev4.cypher.catalog.CatalogValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Traduz erro de negócio do cadastro no status que ele carrega.
 *
 * <p>O corpo traz a mensagem porque o gestor precisa ler o motivo: um 409 sem
 * texto vira "algo deu errado" na tela, que é falha silenciosa com outra roupa.
 */
@RestControllerAdvice
public class CatalogExceptionHandler {

    @ExceptionHandler(CatalogValidationException.class)
    public ResponseEntity<Map<String, String>> onValidation(CatalogValidationException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("erro", e.getMessage()));
    }
}
