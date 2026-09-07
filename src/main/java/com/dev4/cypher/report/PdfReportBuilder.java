package com.dev4.cypher.report;

import com.dev4.cypher.dto.PlacarEquipeResponse;
import com.dev4.cypher.dto.SummaryResponse;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Resumo executivo (spec E6 §1.3): combina `/summary` + `/placar` num
 * template HTML/CSS próprio, renderizado a PDF via openhtmltopdf — não
 * reaproveita nenhum componente Angular, é um documento para impressão.
 */
public final class PdfReportBuilder {

    private PdfReportBuilder() {
    }

    public static byte[] resumoExecutivo(SummaryResponse summary, List<PlacarEquipeResponse> placar) throws IOException {
        String html = montarHtml(summary, placar);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        builder.toStream(saida);
        try {
            builder.run();
        } catch (Exception e) {
            throw new IOException("Falha ao gerar PDF de resumo executivo", e);
        }
        return saida.toByteArray();
    }

    private static String montarHtml(SummaryResponse s, List<PlacarEquipeResponse> placar) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>")
            .append("body{font-family:sans-serif;color:#222}h1{color:#F4622A}")
            .append("table{width:100%;border-collapse:collapse;margin-top:8px}")
            .append("td,th{border:1px solid #ccc;padding:6px;text-align:left;font-size:12px}")
            .append("</style></head><body>");

        html.append("<h1>Resumo executivo — Cypher</h1>");
        // SummaryService.build() nao recebe periodo (spec E6 SS1.3) -- os KPIs
        // sao ponto-no-tempo, o rotulo deixa isso explicito para nao sugerir
        // que respeitam o since/until do placar abaixo.
        html.append("<h2>Indicadores (hoje)</h2><ul>")
            .append("<li>Incidentes hoje: ").append(s.incidentesHoje()).append("</li>")
            .append("<li>Incidentes ontem: ").append(s.incidentesOntem()).append("</li>")
            .append("<li>Críticos hoje: ").append(s.criticosHoje()).append("</li>")
            .append("<li>Pendentes: ").append(s.pendentes()).append("</li></ul>");

        html.append("<h2>Heatmap por estação</h2><table><tr><th>Estação</th><th>Setor</th><th>Incidentes</th></tr>");
        for (SummaryResponse.StationCell celula : s.heatmap()) {
            html.append("<tr><td>").append(celula.nome()).append("</td><td>").append(celula.setor())
                .append("</td><td>").append(celula.incidentes()).append("</td></tr>");
        }
        html.append("</table>");

        // Restaurado na revisao de design de 2026-09-08 (spec E6 SS1.3/SS9) --
        // tinha caido de uma edicao anterior da spec por descuido. Dado ja
        // existe em SummaryResponse, sem agregacao nova.
        html.append("<h2>Últimos 7 dias</h2><table><tr><th>Dia</th><th>Incidentes</th></tr>");
        for (SummaryResponse.DayCount dia : s.ultimosSeteDias()) {
            html.append("<tr><td>").append(dia.dia()).append("</td><td>")
                .append(dia.incidentes()).append("</td></tr>");
        }
        html.append("</table>");

        html.append("<h2>Placar de conformidade</h2><table><tr><th>Equipe</th><th>Conformidade</th><th>Cobertura</th></tr>");
        for (PlacarEquipeResponse p : placar) {
            // conformidade() eh nula quando p.dadosSuficientes() == false (E5 SS3.2) --
            // desempacotar direto (p.conformidade() * 100) lanca NPE (achado da
            // revisao de design, 2026-09-08). Mostra "dados insuficientes" em vez de numero.
            String conformidadeTexto = p.dadosSuficientes()
                    ? String.format("%.0f%%", p.conformidade() * 100)
                    : "dados insuficientes";
            html.append("<tr><td>").append(p.nome()).append("</td><td>")
                .append(conformidadeTexto).append("</td><td>")
                .append(String.format("%.0f%%", p.cobertura() * 100)).append("</td></tr>");
        }
        html.append("</table></body></html>");
        return html.toString();
    }
}
