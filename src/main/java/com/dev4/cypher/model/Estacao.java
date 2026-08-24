package com.dev4.cypher.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Unidade de lugar: uma máquina ou posto de trabalho, coberto por uma ou mais
 * câmeras. É a célula do heatmap e o eixo do mapa da fábrica.
 *
 * <p>{@code setor} é campo texto livre, sem entidade própria (spec §12).
 */
@Entity
@Table(name = "estacoes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Estacao {

    /** Identificador legível, ex.: EST-01. */
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 120)
    private String setor;

    @Column(nullable = false)
    private boolean ativa;

    /** Opaco, gerado no cadastro (`TokenGenerator`). Exposto só via `EstacaoAdminResponse` (Tarefa 5). */
    @Column(nullable = false, unique = true, length = 80)
    private String token;
}
