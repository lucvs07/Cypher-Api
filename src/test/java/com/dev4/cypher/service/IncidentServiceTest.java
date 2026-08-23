package com.dev4.cypher.service;

import com.dev4.cypher.dto.IncidentCloseRequest;
import com.dev4.cypher.dto.IncidentRequest;
import com.dev4.cypher.mapper.IncidentMapper;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.model.AlertaTipo;
import com.dev4.cypher.model.ClosedReason;
import com.dev4.cypher.repository.AlertaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class IncidentServiceTest {

    @Autowired
    private AlertaRepository repository;

    private IncidentService service;

    @BeforeEach
    void setUp() {
        // Publisher no-op: a publicação de eventos é verificada em
        // IncidentEventPublicationTest, que exercita o commit real.
        ApplicationEventPublisher noop = event -> { };
        service = new IncidentService(repository, new IncidentMapper(), noop);
    }

    private IncidentRequest queda(String key) {
        return new IncidentRequest(
                key, "CAM-01", "run-a", 7, AlertaTipo.FALL_RISK, 4,
                "fallen_sustained", "immediate_risk", 11, 4, null, "outside",
                List.of("HELMET"), List.of(),
                OffsetDateTime.of(2026, 8, 10, 14, 31, 2, 0, ZoneOffset.ofHours(-3)),
                null);
    }

    @Test
    void criaIncidenteNaPrimeiraIngestao() {
        IncidentService.IngestResult resultado = service.ingest(queda("k1"));

        assertThat(resultado.created()).isTrue();
        assertThat(resultado.incident().id()).isNotNull();
        assertThat(resultado.incident().status()).isEqualTo(AlertaStatus.PENDENTE);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void ingestaoRepetidaDevolveOExistenteSemDuplicar() {
        IncidentService.IngestResult primeiro = service.ingest(queda("k1"));
        IncidentService.IngestResult segundo = service.ingest(queda("k1"));

        assertThat(segundo.created()).isFalse();
        assertThat(segundo.incident().id()).isEqualTo(primeiro.incident().id());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void aceitaIncidenteDeCameraNaoCadastrada() {
        IncidentRequest desconhecida = new IncidentRequest(
                "k-x", "CAM-99", "run-a", 1, AlertaTipo.ERGONOMIC_RISK, 2,
                "reba_9", "ergonomically_inadequate", 9, 3, null, "outside",
                List.of(), List.of(),
                OffsetDateTime.of(2026, 8, 10, 10, 0, 0, 0, ZoneOffset.UTC), null);

        IncidentService.IngestResult resultado = service.ingest(desconhecida);

        assertThat(resultado.created()).isTrue();
        assertThat(resultado.incident().cameraId()).isEqualTo("CAM-99");
    }

    @Test
    void fechaEpisodioComDuracaoEPico() {
        service.ingest(queda("k1"));

        Optional<com.dev4.cypher.dto.IncidentResponse> fechado = service.close("k1",
                new IncidentCloseRequest(
                        OffsetDateTime.of(2026, 8, 10, 14, 31, 9, 0, ZoneOffset.ofHours(-3)),
                        6.6, 4, ClosedReason.STATE_EXIT, null));

        assertThat(fechado).isPresent();
        assertThat(fechado.get().durationSeconds()).isEqualTo(6.6);
        assertThat(fechado.get().peakSeverity()).isEqualTo(4);
        assertThat(fechado.get().closedReason()).isEqualTo(ClosedReason.STATE_EXIT);
    }

    @Test
    void aplicaApenasOsCamposPresentesNoFechamento() {
        service.ingest(queda("k1"));
        service.close("k1", new IncidentCloseRequest(null, null, null, null,
                "https://cdn/snap.jpg"));

        Optional<com.dev4.cypher.dto.IncidentResponse> lido = service.close("k1",
                new IncidentCloseRequest(null, null, null, null, null));

        assertThat(lido).isPresent();
        assertThat(lido.get().snapshotUrl()).isEqualTo("https://cdn/snap.jpg");
        assertThat(lido.get().endedAt()).isNull();
    }

    @Test
    void fechamentoDeChaveInexistenteDevolveVazio() {
        assertThat(service.close("nao-existe",
                new IncidentCloseRequest(null, null, null, null, null))).isEmpty();
    }

    @Test
    void mudaStatusDoIncidente() {
        Long id = service.ingest(queda("k1")).incident().id();

        Optional<com.dev4.cypher.dto.IncidentResponse> revisado =
                service.changeStatus(id, AlertaStatus.REVISADO);

        assertThat(revisado).isPresent();
        assertThat(revisado.get().status()).isEqualTo(AlertaStatus.REVISADO);
    }

    @Test
    void buscaIncidentePorId() {
        Long id = service.ingest(queda("k1")).incident().id();

        Optional<com.dev4.cypher.dto.IncidentResponse> encontrado = service.findById(id);

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().id()).isEqualTo(id);
        assertThat(encontrado.get().incidentKey()).isEqualTo("k1");
    }

    @Test
    void buscaPorIdInexistenteDevolveVazio() {
        assertThat(service.findById(999999L)).isEmpty();
    }
}
