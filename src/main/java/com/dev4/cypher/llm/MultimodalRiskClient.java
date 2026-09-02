package com.dev4.cypher.llm;

/** Porta para um provedor de LLM multimodal (spec §2.4) — alternável só por config. */
public interface MultimodalRiskClient {
    /** Devolve o texto bruto respondido pelo modelo; pode nao ser JSON valido. */
    String analisar(String imagemUrl, String prompt);
}
