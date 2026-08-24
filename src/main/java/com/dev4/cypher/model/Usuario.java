package com.dev4.cypher.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Gestor autenticado por usuário/senha. É o único papel que esta entidade
 * representa — não existe coluna de papel: {@code OPERADOR} nunca é um
 * {@code Usuario}, autentica pelo token da própria {@code Estacao} (spec E4
 * §2.5). {@code UsuarioResponse.papel} devolve sempre a string literal
 * {@code "GESTOR"}.
 */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    /** BCrypt, nunca texto puro — gravado só pelo `PasswordEncoder` (Tarefa 1). */
    @Column(nullable = false, length = 100)
    private String passwordHash;
}
