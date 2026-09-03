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
}
