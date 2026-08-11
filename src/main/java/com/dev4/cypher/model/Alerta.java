package com.dev4.cypher.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Incidente de segurança detectado pelo pipeline de visão computacional.
 *
 * <p>Representa um episódio com início e fim, não um fato pontual: o pipeline
 * abre o incidente na confirmação do alarme e o fecha quando o risco cessa.
 *
 * <p>{@code cameraId} não tem chave estrangeira para {@link Camera} de
 * propósito — um incidente vindo de câmera não cadastrada é aceito
 * normalmente (spec §12).
 */
@Entity
@Table(name = "alertas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Chave de idempotência: cameraId:runId:trackId:timestampDeAbertura. */
    @Column(nullable = false, unique = true, length = 200)
    private String incidentKey;

    /** UUID da execução do pipeline; trackId só é único dentro dele. */
    @Column(nullable = false, length = 64)
    private String runId;

    @Column(nullable = false)
    private Integer trackId;

    @Column(nullable = false, length = 64)
    private String cameraId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertaTipo tipo;

    /** Severidade crua 0–4 vinda do motor de decisão. */
    @Column(nullable = false)
    private Integer severidade;

    /** Derivado de severidade via AlertaNivel.fromSeverity. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertaNivel nivel;

    @Column(nullable = false, length = 300)
    private String descricao;

    /** Rótulo da regra que disparou: fallen_sustained, reba_11, … */
    @Column(nullable = false, length = 100)
    private String reason;

    @Column(nullable = false, length = 50)
    private String posturaCategoria;

    @Column(nullable = false)
    private Integer rebaScore;

    @Column(nullable = false)
    private Integer actionLevel;

    /** Nome da zona crítica; nulo quando a pessoa está fora de qualquer zona. */
    @Column(length = 120)
    private String zona;

    @Column(nullable = false, length = 20)
    private String zoneStatus;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "alerta_epi_present", joinColumns = @JoinColumn(name = "alerta_id"))
    @Column(name = "epi")
    private List<String> epiPresent = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "alerta_epi_missing", joinColumns = @JoinColumn(name = "alerta_id"))
    @Column(name = "epi")
    private List<String> epiMissing = new ArrayList<>();

    @Column(length = 500)
    private String snapshotUrl;

    @Column(nullable = false)
    private OffsetDateTime dataHora;

    private OffsetDateTime encerradoEm;

    private Double duracaoSegundos;

    private Integer severidadePico;

    @Enumerated(EnumType.STRING)
    private ClosedReason closedReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertaStatus status;
}
