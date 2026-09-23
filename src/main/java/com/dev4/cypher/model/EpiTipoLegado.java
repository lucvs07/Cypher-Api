package com.dev4.cypher.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Leitura tolerante das colunas de EPI gravadas antes de o contrato ser só
 * {@link EpiTipo} (2026-09-22).
 *
 * <p>Estas colunas guardavam string livre: a classe crua do detector
 * ({@code Goggles}, {@code boots}), o rótulo de violação ({@code no_goggle},
 * {@code no_gloves}) e, em zona, a chave em português do onboarding
 * ({@code capacete}). O banco é H2 com {@code ddl-auto: update}, sem migração
 * versionada — então a entidade persiste {@code List<String>} e só expõe
 * {@code List<EpiTipo>}: a leitura aceita os formatos antigos, a gravação é
 * sempre {@link EpiTipo#name()}, e cada linha se normaliza quando for
 * regravada. Valor que não é EPI detectável ({@code auricular},
 * {@code cinto}, lixo) é descartado com aviso em vez de derrubar a leitura
 * do registro inteiro.
 *
 * <p>(Não é um {@code AttributeConverter}: com conversor de enum o Hibernate
 * gera um CHECK na coluna que o H2 2.4 não consegue avaliar, e a coluna
 * precisa aceitar valor legado de qualquer forma.)
 *
 * <p>Pode sair quando não houver mais linha legada no banco.
 */
public final class EpiTipoLegado {

    private static final Logger log = LoggerFactory.getLogger(EpiTipoLegado.class);

    private static final Map<String, EpiTipo> LEGADO = Map.ofEntries(
            // rótulo de violação do pipeline
            Map.entry("no_helmet", EpiTipo.HELMET),
            Map.entry("no_goggle", EpiTipo.GOGGLES),
            Map.entry("no_gloves", EpiTipo.GLOVE),
            Map.entry("no_vest", EpiTipo.VEST),
            Map.entry("no_boots", EpiTipo.BOOTS),
            Map.entry("no_mask", EpiTipo.MASK),
            // chave em português do onboarding
            Map.entry("capacete", EpiTipo.HELMET),
            Map.entry("oculos", EpiTipo.GOGGLES),
            Map.entry("luvas", EpiTipo.GLOVE),
            Map.entry("colete", EpiTipo.VEST),
            Map.entry("botas", EpiTipo.BOOTS),
            Map.entry("mascara", EpiTipo.MASK)
    );

    private EpiTipoLegado() {
    }

    /** {@code null} quando o valor não é um EPI detectável. */
    public static EpiTipo ler(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.strip();
        try {
            // Canônico em qualquer caixa — cobre também a classe crua do
            // detector (Goggles, boots, GLOVE, ...).
            return EpiTipo.valueOf(limpo.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException naoCanonico) {
            EpiTipo legado = LEGADO.get(limpo.toLowerCase(Locale.ROOT));
            if (legado == null) {
                log.warn("EPI legado '{}' ignorado na leitura: nao e um EPI detectavel", valor);
            }
            return legado;
        }
    }

    public static List<EpiTipo> lerTodos(List<String> valores) {
        return valores.stream().map(EpiTipoLegado::ler).filter(Objects::nonNull).distinct().toList();
    }

    public static List<String> gravar(List<EpiTipo> tipos) {
        return tipos == null ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(tipos.stream().map(EpiTipo::name).toList());
    }
}
