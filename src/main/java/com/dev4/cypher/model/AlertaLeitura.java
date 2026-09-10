package com.dev4.cypher.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Estado de leitura de uma notificação (= um {@link Alerta}) por usuário
 * GESTOR. Sem coluna de "lido": a presença desta linha para
 * (empresaId, usuario, alertaId) já significa lido; ausência = não lido
 * (spec central-notificacoes §2).
 */
@Entity
@Table(name = "alerta_leitura",
       uniqueConstraints = @UniqueConstraint(columnNames = {"empresa_id", "usuario", "alerta_id"}))
@Getter
@Setter
public class AlertaLeitura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false, length = 30)
    private String empresaId;

    /** Username do GESTOR ("nome@empresaId", {@link com.dev4.cypher.security.TenantContext#username}) — não há Usuario.id acessível na Authentication atual. */
    @Column(nullable = false, length = 60)
    private String usuario;

    @Column(name = "alerta_id", nullable = false)
    private Long alertaId;

    @Column(name = "lido_em", nullable = false)
    private OffsetDateTime lidoEm;
}
