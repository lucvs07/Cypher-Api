package com.dev4.cypher.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertaTipo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertaNivel nivel;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false)
    private String zona;

    @Column(nullable = false)
    private String cameraId;

    @Column(nullable = false)
    private LocalDateTime dataHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertaStatus status;
}