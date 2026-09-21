package com.dev4.cypher.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
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
 * Unidade de isolamento (tenant): uma empresa cliente do SaaS.
 *
 * <p>{@code id} e o slug escolhido pela propria empresa no sign-up (ex.:
 * "acme"), imutavel apos criado — compoe {@code Usuario.username}
 * ("gestor@acme") e a chave armazenada de todo recurso de catalogo
 * ("acme:EST-01"), entao renomear reescreveria identidade em todo o
 * historico (spec §2.1, mesmo motivo de codigo de estacao/camera ser
 * imutavel).
 */
@Entity
@Table(name = "empresas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Empresa {

    @Id
    @Column(length = 30)
    private String id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false)
    private boolean ativa;

    /**
     * Checklist global de EPI da empresa (achado de campo 2026-09-20):
     * escopo default para a inferência de ausência do pipeline quando a
     * pessoa não está numa zona com {@code required_ppe} próprio — zona,
     * quando resolve algum item, tem prioridade sobre este checklist.
     */
    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "empresa_epi_checklist", joinColumns = @JoinColumn(name = "empresa_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "epi", nullable = false)
    private List<EpiTipo> episMonitorados = new ArrayList<>();

    /** Construtor de compatibilidade com o formato anterior (sem checklist de EPI). */
    public Empresa(String id, String nome, boolean ativa) {
        this(id, nome, ativa, new ArrayList<>());
    }
}
