package com.dev4.cypher.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/**
 * Câmera vinculada a uma estação.
 *
 * <p>{@code streamUrl} é o MJPEG servido pelo próprio pipeline Python e
 * consumido direto pelo browser; {@code fonte} é o que o pipeline abre
 * (índice de webcam, caminho de arquivo ou URL RTSP) e só passa a ser usado
 * em E2.
 */
@Entity
@Table(name = "cameras")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Camera {

    /** Identificador legível, ex.: CAM-01. */
    @Id
    @Column(length = 100)
    private String id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 100)
    private String estacaoId;

    @Column(length = 500)
    private String streamUrl;

    @Column(length = 500)
    private String fonte;

    /**
     * Câmera em operação. Desativar tira do heatmap e do seletor de feed sem
     * apagar o histórico: não há chave estrangeira entre incidente e câmera,
     * então apagar de verdade deixaria incidentes órfãos.
     */
    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean ativa;

    /**
     * Opaco, gerado no cadastro (`TokenGenerator`). Nunca aparece em
     * `CameraAdminResponse` (GET/create/update) — a única forma de lê-la é
     * `POST /cameras/{id}/apikey/regenerate`, que sempre rotaciona (mesmo
     * padrão de `Estacao.token`). Gap de provisionamento fechado pós-E4
     * (revisado em 2026-08-25, ver spec E4 §2.3).
     */
    @Column(nullable = false, unique = true, length = 80)
    private String apiKey;

    @Column(nullable = false, length = 30)
    private String empresaId;

    /** Graus horários para corrigir câmera montada virada; aplicado ao frame inteiro pelo pipeline. */
    @ColumnDefault("0")
    @Column(nullable = false)
    private int rotacao;

    /** Roll fino (-45 a 45) usado só no cálculo de flexão de tronco do REBA, não gira o frame. */
    @ColumnDefault("0")
    @Column(nullable = false)
    private int anguloCameraGraus;

    /**
     * Construtor de compatibilidade com o formato anterior (sem rotacao/anguloCameraGraus),
     * usado pelos testes existentes. Lombok gera separadamente o @AllArgsConstructor com os
     * 10 campos atuais — os dois convivem porque têm aridade diferente.
     */
    public Camera(String id, String nome, String estacaoId, String streamUrl, String fonte,
                  boolean ativa, String apiKey, String empresaId) {
        this(id, nome, estacaoId, streamUrl, fonte, ativa, apiKey, empresaId, 0, 0);
    }
}
