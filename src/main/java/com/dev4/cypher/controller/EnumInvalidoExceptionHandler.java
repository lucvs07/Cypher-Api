package com.dev4.cypher.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Corpo com valor fora de um enum (ex.: {@code requiredPpe: ["capacete"]},
 * {@code epiMissing: ["no_helmet"]}) continua 400, mas com a lista do que é
 * aceito — sem isso o cliente só vê "Bad Request" e não sabe qual campo nem
 * qual valor corrigir.
 */
@RestControllerAdvice
public class EnumInvalidoExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> onCorpoIlegivel(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof InvalidFormatException formato
                && formato.getTargetType() != null && formato.getTargetType().isEnum()) {
            String aceitos = Arrays.stream(formato.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            return ResponseEntity.badRequest().body(Map.of("erro",
                    "valor invalido '%s' para %s (esperado um de: %s)"
                            .formatted(formato.getValue(), formato.getTargetType().getSimpleName(), aceitos)));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", "corpo da requisicao invalido"));
    }
}
