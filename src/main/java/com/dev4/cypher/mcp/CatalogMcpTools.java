package com.dev4.cypher.mcp;

import com.dev4.cypher.dto.CameraResponse;
import com.dev4.cypher.dto.StationResponse;
import com.dev4.cypher.service.CatalogService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/** Tools MCP de catálogo (câmeras/estações) — delega inteiramente para CatalogService. */
@Component
public class CatalogMcpTools {

    private final CatalogService catalog;

    public CatalogMcpTools(CatalogService catalog) {
        this.catalog = catalog;
    }

    @McpTool(name = "list_cameras", description = "Lista as câmeras cadastradas na empresa.")
    public List<CameraResponse> listCameras(
            @McpToolParam(description = "Incluir câmeras inativas", required = true) boolean incluirInativas) {
        return catalog.cameras(incluirInativas);
    }

    @McpTool(name = "list_stations", description = "Lista as estações cadastradas na empresa.")
    public List<StationResponse> listStations(
            @McpToolParam(description = "Incluir estações inativas", required = true) boolean incluirInativas) {
        return catalog.stations(incluirInativas);
    }
}
