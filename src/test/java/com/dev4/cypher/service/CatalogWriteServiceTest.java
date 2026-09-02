package com.dev4.cypher.service;

import com.dev4.cypher.catalog.CatalogValidationException;
import com.dev4.cypher.dto.CameraAdminResponse;
import com.dev4.cypher.dto.CameraRequest;
import com.dev4.cypher.dto.EstacaoAdminResponse;
import com.dev4.cypher.dto.StationRequest;
import com.dev4.cypher.dto.StationResponse;
import com.dev4.cypher.model.Camera;
import com.dev4.cypher.repository.CameraRepository;
import com.dev4.cypher.repository.EstacaoRepository;
import com.dev4.cypher.security.GestorAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class CatalogWriteServiceTest {

    @Autowired
    private EstacaoRepository estacoes;

    @Autowired
    private CameraRepository cameras;

    private CatalogService service;

    @BeforeEach
    void setUp() {
        service = new CatalogService(estacoes, cameras);
        SecurityContextHolder.getContext().setAuthentication(
                new GestorAuthentication("gestor@demo", "demo"));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private StationRequest esteira() {
        return new StationRequest("EST-01", "Esteira de montagem", "montagem", null);
    }

    @Test
    void cadastraEstacaoAtivaPorDefault() {
        EstacaoAdminResponse criada = service.createStation(esteira());

        assertThat(criada.id()).isEqualTo("EST-01");
        assertThat(criada.nome()).isEqualTo("Esteira de montagem");
        assertThat(criada.setor()).isEqualTo("montagem");
        assertThat(criada.ativa()).isTrue();
        assertThat(criada.token()).isNotBlank();
    }

    @Test
    void recusaCodigoDuplicado() {
        service.createStation(esteira());

        assertThatThrownBy(() -> service.createStation(esteira()))
                .isInstanceOf(CatalogValidationException.class)
                .satisfies(e -> assertThat(((CatalogValidationException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void editaNomeESetor() {
        service.createStation(esteira());

        Optional<EstacaoAdminResponse> editada = service.updateStation("EST-01",
                new StationRequest("EST-01", "Esteira 1", "montagem final", null));

        assertThat(editada).isPresent();
        assertThat(editada.get().nome()).isEqualTo("Esteira 1");
        assertThat(editada.get().setor()).isEqualTo("montagem final");
    }

    @Test
    void recusaTentativaDeRenomearOCodigo() {
        service.createStation(esteira());

        assertThatThrownBy(() -> service.updateStation("EST-01",
                new StationRequest("EST-99", "Esteira", "montagem", null)))
                .isInstanceOf(CatalogValidationException.class)
                .satisfies(e -> assertThat(((CatalogValidationException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void editarEstacaoInexistenteDevolveVazio() {
        assertThat(service.updateStation("EST-99",
                new StationRequest("EST-99", "X", null, null))).isEmpty();
    }

    @Test
    void ativaNuloNaoMexeNoEstado() {
        service.createStation(esteira());
        service.deactivateStation("EST-01");

        service.updateStation("EST-01",
                new StationRequest("EST-01", "Esteira", "montagem", null));

        assertThat(estacoes.findById("demo:EST-01").orElseThrow().isAtiva()).isFalse();
    }

    @Test
    void reativarEhUmPutComAtivaVerdadeira() {
        service.createStation(esteira());
        service.deactivateStation("EST-01");

        service.updateStation("EST-01",
                new StationRequest("EST-01", "Esteira", "montagem", true));

        assertThat(estacoes.findById("demo:EST-01").orElseThrow().isAtiva()).isTrue();
    }

    @Test
    void desativarNaoApaga() {
        service.createStation(esteira());

        assertThat(service.deactivateStation("EST-01")).isTrue();

        assertThat(estacoes.findById("demo:EST-01")).isPresent();
        assertThat(estacoes.findById("demo:EST-01").orElseThrow().isAtiva()).isFalse();
    }

    @Test
    void desativarInexistenteDevolveFalso() {
        assertThat(service.deactivateStation("EST-99")).isFalse();
    }

    @Test
    void listaPadraoOmiteEstacaoDesativada() {
        service.createStation(esteira());
        service.createStation(new StationRequest("EST-02", "Prensa", "estamparia", null));
        service.deactivateStation("EST-02");

        assertThat(service.stations()).extracting(StationResponse::id)
                .containsExactly("EST-01");
        assertThat(service.stations(true)).extracting(StationResponse::id)
                .containsExactlyInAnyOrder("EST-01", "EST-02");
    }

    @Test
    void estacaoAtivaNaoMostraCameraDesativada() {
        service.createStation(esteira());
        cameras.save(new Camera("demo:CAM-01", "Frontal", "demo:EST-01",
                "http://127.0.0.1:8090/stream", "rtsp://a", true, "key-CAM-01", "demo"));
        cameras.saveAndFlush(new Camera("demo:CAM-02", "Antiga", "demo:EST-01",
                "http://127.0.0.1:8091/stream", "rtsp://b", false, "key-CAM-02", "demo"));

        StationResponse estacao = service.stations().get(0);

        assertThat(estacao.cameras()).hasSize(1);
        assertThat(estacao.cameras().get(0).id()).isEqualTo("CAM-01");
    }

    @Test
    void listaDeCamerasOmiteDesativadas() {
        cameras.save(new Camera("demo:CAM-01", "Frontal", "demo:EST-01",
                "http://127.0.0.1:8090/stream", "rtsp://a", true, "key-CAM-01", "demo"));
        cameras.saveAndFlush(new Camera("demo:CAM-02", "Antiga", "demo:EST-01",
                "http://127.0.0.1:8091/stream", "rtsp://b", false, "key-CAM-02", "demo"));

        assertThat(service.cameras()).hasSize(1);
    }

    private CameraRequest frontal() {
        return new CameraRequest("CAM-01", "Frontal", "EST-01",
                "http://127.0.0.1:8090/stream",
                "rtsp://${CAM01_USER}:${CAM01_PASS}@10.0.0.7/stream", null);
    }

    @Test
    void cadastraCameraNaEstacaoExistente() {
        service.createStation(esteira());

        CameraAdminResponse criada = service.createCamera(frontal());

        assertThat(criada.id()).isEqualTo("CAM-01");
        assertThat(criada.estacaoId()).isEqualTo("EST-01");
        assertThat(criada.ativa()).isTrue();
        assertThat(criada.fonte()).contains("${CAM01_USER}");
    }

    @Test
    void recusaCameraDeEstacaoInexistente() {
        assertThatThrownBy(() -> service.createCamera(frontal()))
                .isInstanceOf(CatalogValidationException.class)
                .satisfies(e -> assertThat(((CatalogValidationException) e).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void recusaCodigoDeCameraDuplicado() {
        service.createStation(esteira());
        service.createCamera(frontal());

        assertThatThrownBy(() -> service.createCamera(frontal()))
                .isInstanceOf(CatalogValidationException.class)
                .satisfies(e -> assertThat(((CatalogValidationException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void recusaStreamUrlDuplicada() {
        service.createStation(esteira());
        service.createCamera(frontal());

        assertThatThrownBy(() -> service.createCamera(new CameraRequest(
                "CAM-02", "Lateral", "EST-01",
                "http://127.0.0.1:8090/stream", "rtsp://outra", null)))
                .isInstanceOf(CatalogValidationException.class)
                .satisfies(e -> assertThat(((CatalogValidationException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void recusaStreamUrlSemPortaExplicita() {
        service.createStation(esteira());

        assertThatThrownBy(() -> service.createCamera(new CameraRequest(
                "CAM-02", "Lateral", "EST-01",
                "http://127.0.0.1/stream", "rtsp://x", null)))
                .isInstanceOf(CatalogValidationException.class)
                .satisfies(e -> assertThat(((CatalogValidationException) e).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void recusaRenomearCodigoDeCamera() {
        service.createStation(esteira());
        service.createCamera(frontal());

        assertThatThrownBy(() -> service.updateCamera("CAM-01", new CameraRequest(
                "CAM-99", "Frontal", "EST-01",
                "http://127.0.0.1:8090/stream", "rtsp://x", null)))
                .isInstanceOf(CatalogValidationException.class);
    }

    @Test
    void editaCameraMantendoAMesmaUrl() {
        service.createStation(esteira());
        service.createCamera(frontal());

        Optional<CameraAdminResponse> editada = service.updateCamera("CAM-01",
                new CameraRequest("CAM-01", "Frontal HD", "EST-01",
                        "http://127.0.0.1:8090/stream", "rtsp://nova", null));

        assertThat(editada).isPresent();
        assertThat(editada.get().nome()).isEqualTo("Frontal HD");
        assertThat(editada.get().fonte()).isEqualTo("rtsp://nova");
    }

    @Test
    void desativarCameraNaoApagaEContinuaVisivelPorId() {
        service.createStation(esteira());
        service.createCamera(frontal());

        assertThat(service.deactivateCamera("CAM-01")).isTrue();

        // A câmera precisa continuar visível para poder ser reativada.
        assertThat(service.camera("CAM-01")).isPresent();
        assertThat(service.camera("CAM-01").orElseThrow().ativa()).isFalse();
        assertThat(service.cameras()).isEmpty();
    }

    @Test
    void reativarCameraEhUmPutComAtivaVerdadeira() {
        service.createStation(esteira());
        service.createCamera(frontal());
        service.deactivateCamera("CAM-01");

        service.updateCamera("CAM-01", new CameraRequest("CAM-01", "Frontal", "EST-01",
                "http://127.0.0.1:8090/stream", "rtsp://x", true));

        assertThat(service.cameras()).extracting(c -> c.id()).containsExactly("CAM-01");
    }

    @Test
    void cameraInexistenteDevolveVazio() {
        assertThat(service.camera("CAM-99")).isEmpty();
        assertThat(service.deactivateCamera("CAM-99")).isFalse();
    }
}
