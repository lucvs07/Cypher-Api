package com.dev4.cypher.report;

import com.dev4.cypher.dto.IncidentResponse;
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

    private CsvReportBuilder() {
    }

    public static String incidentesParaCsv(List<IncidentResponse> incidentes) throws IOException {
        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(CABECALHO_INCIDENTES).build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (IncidentResponse i : incidentes) {
                printer.printRecord(i.id(), i.incidentKey(), i.cameraId(), i.type(), i.severity(),
                        i.nivel(), i.reason(), i.zoneName(), i.detectedAt(), i.endedAt(),
                        i.durationSeconds(), i.status());
            }
        }
        return writer.toString();
    }
}
