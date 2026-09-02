package com.dev4.cypher.llm;

import com.dev4.cypher.catalog.CatalogValidationException;
import com.dev4.cypher.config.LlmProperties;
import com.dev4.cypher.dto.RiskAnalysisResponse;
import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.model.IncidentLlmAnalysis;
import com.dev4.cypher.repository.AlertaRepository;
import com.dev4.cypher.repository.IncidentLlmAnalysisRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;

/** Orquestra a analise de risco por LLM para um incidente (spec §2.5). */
@Service
public class LlmAnalysisService {

    private final AlertaRepository alertas;
    private final IncidentLlmAnalysisRepository analyses;
    private final MultimodalRiskClient client;
    private final PromptBuilder promptBuilder;
    private final LlmResponseParser parser;
    private final LlmProperties properties;

    /**
     * 3 tentativas de retry (4 no total), 1s/2s/4s de base com jitter — so
     * para falha transitoria (5xx, 429, timeout/rede). Contorna o 503 de
     * "high demand" do nivel gratuito do Gemini sem mascarar erro real
     * (chave invalida, request malformado nunca sao retentados).
     */
    private final RetryComBackoff retry = new RetryComBackoff(3, Duration.ofSeconds(1));

    public LlmAnalysisService(AlertaRepository alertas, IncidentLlmAnalysisRepository analyses,
                               MultimodalRiskClient client, PromptBuilder promptBuilder,
                               LlmResponseParser parser, LlmProperties properties) {
        this.alertas = alertas;
        this.analyses = analyses;
        this.client = client;
        this.promptBuilder = promptBuilder;
        this.parser = parser;
        this.properties = properties;
    }

    public RiskAnalysisResponse analisar(Long alertaId) {
        Alerta alerta = alertas.findById(alertaId)
                .orElseThrow(() -> new NoSuchElementException("Incidente nao encontrado: " + alertaId));

        if (alerta.getSnapshotUrl() == null) {
            throw new CatalogValidationException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Nenhuma imagem disponivel para analise deste incidente");
        }

        String prompt = promptBuilder.montar(alerta, properties.getPromptVersion());
        String bruto = null;
        String erro = null;
        RiskAnalysisResponse resultado;
        try {
            bruto = retry.executar(() -> client.analisar(alerta.getSnapshotUrl(), prompt),
                    RetryComBackoff::transitoria);
            resultado = parser.parse(bruto, properties.getProvider(), properties.getPromptVersion());
        } catch (Exception falhaApi) {
            erro = truncar(falhaApi.getMessage());
            resultado = RiskAnalysisResponse.erroApi(properties.getProvider(), properties.getPromptVersion());
        }

        analyses.save(new IncidentLlmAnalysis(alertaId, resultado.provider(), resultado.promptVersion(),
                resultado.status(), bruto, erro, OffsetDateTime.now()));

        return resultado;
    }

    /** IncidentLlmAnalysis.erro nao tem @Column(length=...) explicito: varchar(255) por padrao. */
    private static final int TAMANHO_MAXIMO_ERRO = 255;

    private String truncar(String mensagem) {
        if (mensagem == null) {
            return null;
        }
        return mensagem.length() > TAMANHO_MAXIMO_ERRO
                ? mensagem.substring(0, TAMANHO_MAXIMO_ERRO) : mensagem;
    }
}
