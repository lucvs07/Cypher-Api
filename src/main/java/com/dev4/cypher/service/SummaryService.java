package com.dev4.cypher.service;

import com.dev4.cypher.dto.CameraResponse;
import com.dev4.cypher.dto.StationResponse;
import com.dev4.cypher.dto.SummaryResponse;
import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.repository.AlertaRepository;
import com.dev4.cypher.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agregações do dashboard.
 *
 * <p>A janela de sete dias é curta o bastante para carregar os incidentes do
 * período em memória e contar em Java. Se o volume crescer, a substituição é
 * por consultas de agregação — mas otimizar antes disso seria adivinhação.
 */
@Service
public class SummaryService {

    private final AlertaRepository repository;
    private final CatalogService catalogo;

    public SummaryService(AlertaRepository repository, CatalogService catalogo) {
        this.repository = repository;
        this.catalogo = catalogo;
    }

    @Transactional(readOnly = true)
    public SummaryResponse build() {
        return build(null, null, null, null, null, false);
    }

    @Transactional(readOnly = true)
    public SummaryResponse build(OffsetDateTime since, OffsetDateTime until,
                                  String turno, String setor, String epi, boolean detalhado) {
        ZoneId zona = ZoneId.systemDefault();
        LocalDate hoje = LocalDate.now(zona);
        LocalDate ontem = hoje.minusDays(1);
        OffsetDateTime inicioDaJanela = hoje.minusDays(6).atStartOfDay(zona).toOffsetDateTime();

        // Escopado por tenant (achado de segurança carregado da Tarefa 4):
        // sem isso, todo GESTOR enxergava as contagens/heatmap de QUALQUER
        // empresa. Uma única busca alimenta tanto a janela de 7 dias quanto
        // os pendentes — ambos eram `findAll()` antes desta correção.
        List<Alerta> doTenant = repository.findByEmpresaId(TenantContext.currentEmpresaId());

        List<Alerta> janela = doTenant.stream()
                .filter(a -> !a.getDataHora().isBefore(inicioDaJanela))
                .toList();

        Map<String, String> estacaoPorCamera = new HashMap<>();
        Map<String, StationResponse> estacoes = new HashMap<>();
        for (StationResponse estacao : catalogo.stations()) {
            estacoes.put(estacao.id(), estacao);
            for (CameraResponse camera : estacao.cameras()) {
                estacaoPorCamera.put(camera.id(), estacao.id());
            }
        }

        int incidentesHoje = 0;
        int incidentesOntem = 0;
        int criticosHoje = 0;
        Map<LocalDate, Integer> porDia = new HashMap<>();

        for (Alerta a : janela) {
            LocalDate dia = a.getDataHora().atZoneSameInstant(zona).toLocalDate();
            porDia.merge(dia, 1, Integer::sum);

            if (dia.equals(hoje)) {
                incidentesHoje++;
                if (a.getSeveridade() >= 3) {
                    criticosHoje++;
                }
            } else if (dia.equals(ontem)) {
                incidentesOntem++;
            }
        }

        // Janela do heatmap espacial (e, na Task 4, de distribuicaoTemporal):
        // "hoje" continua sendo o default quando since/until nao vem, mas
        // agora e um parametro, nao mais hardcoded (spec SS1.1).
        OffsetDateTime desde = since != null ? since : hoje.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime ate = until != null ? until : OffsetDateTime.now(zona);

        Map<String, Integer> porEstacaoNaJanela = new HashMap<>();
        for (Alerta a : doTenant) {
            if (a.getDataHora().isBefore(desde) || a.getDataHora().isAfter(ate)) {
                continue;
            }
            String estacaoId = estacaoPorCamera.get(TenantContext.codigo(a.getCameraId()));
            if (estacaoId != null) {
                porEstacaoNaJanela.merge(estacaoId, 1, Integer::sum);
            }
        }

        // Estações sem incidente aparecem com zero: uma célula ausente e uma
        // célula zerada significam coisas diferentes no heatmap.
        List<SummaryResponse.StationCell> heatmap = estacoes.values().stream()
                .map(e -> new SummaryResponse.StationCell(e.id(), e.nome(), e.setor(),
                        porEstacaoNaJanela.getOrDefault(e.id(), 0)))
                .sorted((a, b) -> a.estacaoId().compareTo(b.estacaoId()))
                .toList();

        List<SummaryResponse.DayCount> serie = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate dia = hoje.minusDays(i);
            serie.add(new SummaryResponse.DayCount(dia, porDia.getOrDefault(dia, 0)));
        }

        int pendentes = (int) doTenant.stream()
                .filter(a -> a.getStatus() == AlertaStatus.PENDENTE)
                .count();

        return new SummaryResponse(incidentesHoje, incidentesOntem, criticosHoje,
                pendentes, heatmap, serie, null, null);
    }
}
