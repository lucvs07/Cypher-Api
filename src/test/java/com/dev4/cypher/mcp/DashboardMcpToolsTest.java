package com.dev4.cypher.mcp;

import com.dev4.cypher.dto.PlacarEquipeResponse;
import com.dev4.cypher.dto.SummaryResponse;
import com.dev4.cypher.service.PlacarService;
import com.dev4.cypher.service.SummaryService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class DashboardMcpToolsTest {

    private final SummaryService summary = Mockito.mock(SummaryService.class);
    private final PlacarService placar = Mockito.mock(PlacarService.class);
    private final DashboardMcpTools tools = new DashboardMcpTools(summary, placar);

    @Test
    void getSummaryDelegaParaSummaryServiceBuild() {
        SummaryResponse resposta = Mockito.mock(SummaryResponse.class);
        when(summary.build(null, null, null, null, null, false)).thenReturn(resposta);

        assertThat(tools.getSummary(null, null, null, null, null, false)).isSameAs(resposta);
    }

    @Test
    void getPlacarResolveJanelaPadraoAntesDeChamarPlacar() {
        OffsetDateTime inicio = OffsetDateTime.now().minusDays(7);
        OffsetDateTime fim = OffsetDateTime.now();
        List<PlacarEquipeResponse> lista = List.of(Mockito.mock(PlacarEquipeResponse.class));
        when(placar.placar(inicio, fim)).thenReturn(lista);

        try (var mocked = Mockito.mockStatic(PlacarService.class)) {
            mocked.when(() -> PlacarService.janelaPadrao(null, null))
                    .thenReturn(new OffsetDateTime[]{inicio, fim});

            assertThat(tools.getPlacar(null, null)).isSameAs(lista);
        }
    }
}
