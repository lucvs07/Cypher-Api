package com.dev4.cypher.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Tempo monitorado agregado por câmera/dia/turno (spec §2.2) — nunca por
 * evento individual: guardar cada heartbeat cresceria sem limite (a cada
 * 30s, 24h/dia, por câmera) sem trazer benefício sobre o agregado diário.
 */
@Entity
@Table(name = "monitoramento_diario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MonitoramentoDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String cameraId;

    @Column(nullable = false, length = 30)
    private String empresaId;

    @Column(nullable = false)
    private LocalDate dia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Turno turno;

    @Column(nullable = false)
    private long segundosMonitorados;

    @Column(nullable = false)
    private OffsetDateTime ultimoHeartbeat;
}
