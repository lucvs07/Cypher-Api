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
 * Conta autenticada por usuário/senha — GESTOR ou OPERADOR (revisão pós-E4:
 * OPERADOR ganhou uma segunda forma de entrar, além do token de estação).
 * {@code estacaoId} só é preenchido para {@code OPERADOR}, vinculando a
 * conta a uma única estação (validado em {@code UsuarioService}, não como
 * constraint de banco — mesmo padrão de {@code Camera.estacaoId}).
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

    @Column(nullable = false, unique = true, length = 90)
    private String username;

    /** BCrypt, nunca texto puro — gravado só pelo `PasswordEncoder` (Tarefa 1). */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String papel;

    /** Só preenchido quando {@code papel} é {@code OPERADOR}. */
    @Column(length = 100)
    private String estacaoId;

    @Column(nullable = false, length = 30)
    private String empresaId;
}
