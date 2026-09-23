package com.dev4.cypher.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.util.ArrayList;
import java.util.List;

/**
 * Zona de risco desenhada sobre uma câmera. `id` é gerado — diferente de
 * `Estacao`/`Camera`, zona não tem contraparte física com nome prévio (spec
 * §2.1). `cameraId` guarda a chave já prefixada de `Camera.id`
 * (`empresaId:codigo`), mesma referência solta sem FK usada em todo o resto
 * do projeto.
 */
@Entity
@Table(name = "zonas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Zona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String cameraId;

    @Column(nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ZoneType zoneType;

    /** Ordem importa — `@OrderColumn` preserva a sequência dos vértices ao recarregar. */
    @ElementCollection
    @CollectionTable(name = "zona_polygon", joinColumns = @JoinColumn(name = "zona_id"))
    @OrderColumn(name = "posicao")
    private List<PontoNormalizado> polygon = new ArrayList<>();

    @Column(nullable = false)
    private int severity;

    @ElementCollection
    @CollectionTable(name = "zona_required_ppe", joinColumns = @JoinColumn(name = "zona_id"))
    @Column(name = "ppe")
    private List<String> requiredPpe = new ArrayList<>();

    /**
     * Quando verdadeiro, esta zona é a área de trabalho: EPI e pose estimation
     * só são avaliados para quem está dentro dela (achado SPI 2026-09-16 —
     * até então zona só controlava severidade de alerta, nunca o que era
     * avaliado). Default falso preserva o comportamento anterior — zona
     * continua sendo só "zona de perigo" a menos que o cadastro marque isto
     * explicitamente.
     */
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean delimitaAvaliacao;

    @Column(nullable = false)
    private boolean ativa;

    @Column(nullable = false, length = 30)
    private String empresaId;

    /** Formatos legados lidos como {@link EpiTipo} — ver {@link EpiTipoLegado}. */
    public List<EpiTipo> getRequiredPpe() {
        return EpiTipoLegado.lerTodos(requiredPpe);
    }

    public void setRequiredPpe(List<EpiTipo> tipos) {
        this.requiredPpe = EpiTipoLegado.gravar(tipos);
    }
}
