package com.dev4.cypher.mcp;

import com.dev4.cypher.dto.CameraResponse;
import com.dev4.cypher.dto.StationResponse;
import com.dev4.cypher.service.CatalogService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class CatalogMcpToolsTest {

    private final CatalogService catalog = Mockito.mock(CatalogService.class);
    private final CatalogMcpTools tools = new CatalogMcpTools(catalog);

    @Test
    void listCamerasDelegaParaCatalogService() {
        List<CameraResponse> lista = List.of(Mockito.mock(CameraResponse.class));
        when(catalog.cameras(false)).thenReturn(lista);

        assertThat(tools.listCameras(false)).isSameAs(lista);
    }

    @Test
    void listStationsDelegaParaCatalogService() {
        List<StationResponse> lista = List.of(Mockito.mock(StationResponse.class));
        when(catalog.stations(true)).thenReturn(lista);

        assertThat(tools.listStations(true)).isSameAs(lista);
    }
}
