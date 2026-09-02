package com.dev4.cypher.repository;

import com.dev4.cypher.model.AnalysisStatus;
import com.dev4.cypher.model.IncidentLlmAnalysis;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class IncidentLlmAnalysisRepositoryTest {

    @Autowired
    private IncidentLlmAnalysisRepository repository;

    @Test
    void salvaERecuperaUmaAnalise() {
        IncidentLlmAnalysis analise = new IncidentLlmAnalysis(42L, "claude", "v2",
                AnalysisStatus.OK, "{\"situacao\":\"ok\"}", null, OffsetDateTime.now());

        IncidentLlmAnalysis salva = repository.save(analise);

        assertThat(salva.getId()).isNotNull();
        assertThat(repository.findById(salva.getId()))
                .isPresent()
                .get()
                .satisfies(a -> {
                    assertThat(a.getAlertaId()).isEqualTo(42L);
                    assertThat(a.getProvider()).isEqualTo("claude");
                    assertThat(a.getStatus()).isEqualTo(AnalysisStatus.OK);
                    assertThat(a.getRespostaJson()).isEqualTo("{\"situacao\":\"ok\"}");
                });
    }

    @Test
    void findFirstByAlertaIdOrderByCriadoEmDescDevolveASemprseMaisRecente() {
        OffsetDateTime agora = OffsetDateTime.now();
        repository.save(new IncidentLlmAnalysis(77L, "claude", "v2", AnalysisStatus.ERRO_API,
                null, "falha antiga", agora.minusMinutes(10)));
        IncidentLlmAnalysis maisRecente = repository.save(new IncidentLlmAnalysis(77L, "gemini", "v2",
                AnalysisStatus.OK, "{\"situacao\":\"recente\"}", null, agora));
        repository.save(new IncidentLlmAnalysis(999L, "claude", "v2", AnalysisStatus.OK,
                "{\"situacao\":\"outro incidente\"}", null, agora));

        var encontrada = repository.findFirstByAlertaIdOrderByCriadoEmDesc(77L);

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getId()).isEqualTo(maisRecente.getId());
        assertThat(encontrada.get().getProvider()).isEqualTo("gemini");
    }

    @Test
    void findFirstByAlertaIdOrderByCriadoEmDescDevolveVazioSemNenhumaAnalise() {
        assertThat(repository.findFirstByAlertaIdOrderByCriadoEmDesc(123456L)).isEmpty();
    }
}
