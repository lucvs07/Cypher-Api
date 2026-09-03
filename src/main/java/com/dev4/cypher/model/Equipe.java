package com.dev4.cypher.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Unidade de alocação: uma equipe cobre uma ou mais estações dentro de um
 * único turno (spec E4 §3, conforme §16.1 da spec de E1 — a unidade é a
 * equipe, não a pessoa; alocação estática, sem escala por data).
 *
 * <p>A unicidade de {@code (estacaoId, turno)} é validada em
 * {@code EquipeService} (Tarefa 9), não como constraint de banco — mesma
 * escolha já feita para {@code setor} em §12 da spec de E1.
 */
@Entity
@Table(name = "equipes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Equipe {

    /** Identificador legível, ex.: EQ-01. */
    @Id
    @Column(length = 100)
    private String id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Turno turno;

    @ElementCollection
    @CollectionTable(name = "equipe_estacoes", joinColumns = @JoinColumn(name = "equipe_id"))
    @Column(name = "estacao_id", length = 100)
    private List<String> estacaoIds = new ArrayList<>();

    private boolean ativa;

    @Column(nullable = false, length = 30)
    private String empresaId;
}
