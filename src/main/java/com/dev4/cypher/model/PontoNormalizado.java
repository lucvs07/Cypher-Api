package com.dev4.cypher.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Vértice de polígono em fração 0.0–1.0 da largura/altura do frame — nunca pixel absoluto (spec §2). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PontoNormalizado {

    @Column(nullable = false)
    private double x;

    @Column(nullable = false)
    private double y;
}
