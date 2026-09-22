package com.dev4.cypher.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** Credencial de acesso ao servidor MCP — um agente de IA externo, escopado por empresa. */
@Entity
@Table(name = "mcp_credentials",
        uniqueConstraints = @UniqueConstraint(columnNames = {"empresa_id", "nome"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class McpCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    /** Hash BCrypt do secret gerado na criação — o valor em texto puro nunca e persistido. */
    @Column(nullable = false, length = 200)
    private String secretHash;

    @Column(nullable = false)
    private boolean ativa;

    @Column(nullable = false, length = 30)
    private String empresaId;

    @Column(nullable = false)
    private OffsetDateTime criadaEm;

    private OffsetDateTime ultimoUsoEm;
}
