package com.dev4.cypher.report;

import com.dev4.cypher.dto.IncidentResponse;
import com.dev4.cypher.dto.PlacarEquipeResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

/**
 * Serialização CSV de projeções já existentes (spec E6 §1) — nenhum estado
 * novo, só troca a representação de JSON paginado para arquivo completo do
 * período. Apache Commons CSV cuida do escaping de vírgula/aspas.
 */
public final class CsvReportBuilder {

    private static final String[] CABECALHO_INCIDENTES = {
            "id", "incidentKey", "cameraId", "type", "severity", "nivel", "reason",
            "zoneName", "detectedAt", "endedAt", "durationSeconds", "status",
    };

    /**
     * Prefixos que planilhas (Excel/LibreOffice) interpretam como início de
     * fórmula ao abrir um CSV. {@code POST /api/v1/incidents} é
     * {@code permitAll} e não sanitiza {@code reason}/{@code zoneName} — um
     * valor como {@code =HYPERLINK(...)} chegaria cru ao CSV sem esta
     * defesa (achado de segurança da revisão final de branch; padrão OWASP
     * de mitigação de CSV Injection/Formula Injection).
     */
    private static final char[] PREFIXOS_DE_FORMULA = {'=', '+', '-', '@', '\t', '\r'};

    private CsvReportBuilder() {
    }

    /**
     * Neutraliza uma célula de texto livre vinda de entrada de usuário: se
     * começar com um dos {@link #PREFIXOS_DE_FORMULA}, prefixa com aspa
     * simples para que a planilha trate o conteúdo como texto puro, nunca
     * como fórmula. Só se aplica a {@link String}; campos numéricos/enum/
     * booleanos não recebem entrada de usuário livre e passam direto.
     */
    private static Object sanitizarCelula(Object valor) {
        if (!(valor instanceof String texto) || texto.isEmpty()) {
            return valor;
        }
        for (char prefixo : PREFIXOS_DE_FORMULA) {
            if (texto.charAt(0) == prefixo) {
                return "'" + texto;
            }
        }
        return texto;
    }

    public static String incidentesParaCsv(List<IncidentResponse> incidentes) throws IOException {
        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(CABECALHO_INCIDENTES).build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (IncidentResponse i : incidentes) {
                printer.printRecord(i.id(), sanitizarCelula(i.incidentKey()), sanitizarCelula(i.cameraId()),
                        i.type(), i.severity(), i.nivel(), sanitizarCelula(i.reason()),
                        sanitizarCelula(i.zoneName()), i.detectedAt(), i.endedAt(),
                        i.durationSeconds(), i.status());
            }
        }
        return writer.toString();
    }

    private static final String[] CABECALHO_PLACAR = {
            "equipeId", "nome", "turno", "dadosSuficientes", "conformidade", "cobertura",
            "sequenciaAtual", "melhorSequencia", "deltaConformidadeSemana",
    };

    /**
     * Metas ({@code metaConformidade} etc.) ficam de fora — são configuração da
     * equipe (já visível em `/config/equipe`), não resultado do período exportado
     * (spec E6 §1.2). {@code conformidade}/{@code deltaConformidadeSemana} nulos
     * (equipe com {@code dadosSuficientes = false}) imprimem célula vazia.
     */
    public static String placarParaCsv(List<PlacarEquipeResponse> placar) throws IOException {
        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(CABECALHO_PLACAR).build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (PlacarEquipeResponse p : placar) {
                printer.printRecord(p.equipeId(), sanitizarCelula(p.nome()), p.turno(), p.dadosSuficientes(),
                        p.conformidade(), p.cobertura(), p.sequenciaAtual(), p.melhorSequencia(),
                        p.deltaConformidadeSemana());
            }
        }
        return writer.toString();
    }
}
