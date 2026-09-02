package com.dev4.cypher.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Registro de uma chamada de análise de risco por LLM (spec §2.3).
 * alertaId é referência solta, sem FK — mesmo padrão do resto do projeto
 * (ver Alerta.cameraId, WebhookDelivery.subscriptionId).
 */
@Entity
@Table(name = "incident_llm_analyses")
@Getter
@Setter
@NoArgsConstructor
public class IncidentLlmAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long alertaId;
    private String provider;
    private String promptVersion;

    @Enumerated(EnumType.STRING)
    private AnalysisStatus status;

    @Lob
    private String respostaJson;

    private String erro;

    private OffsetDateTime criadoEm;

    public IncidentLlmAnalysis(Long alertaId, String provider, String promptVersion,
                                AnalysisStatus status, String respostaJson, String erro,
                                OffsetDateTime criadoEm) {
        this.alertaId = alertaId;
        this.provider = provider;
        this.promptVersion = promptVersion;
        this.status = status;
        this.respostaJson = respostaJson;
        this.erro = erro;
        this.criadoEm = criadoEm;
    }
}
