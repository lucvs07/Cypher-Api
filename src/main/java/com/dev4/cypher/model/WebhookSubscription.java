package com.dev4.cypher.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Assinante externo que recebe incidentes por webhook. */
@Entity
@Table(name = "webhook_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WebhookSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 500)
    private String url;

    @Column(nullable = false, length = 200)
    private String secret;

    /** Só incidentes com severidade ≥ este valor são entregues. */
    @Column(nullable = false)
    private Integer minSeverity;

    @Column(nullable = false)
    private boolean ativo;
}
