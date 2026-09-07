package com.dev4.cypher.service;

import com.dev4.cypher.model.Turno;
import com.dev4.cypher.repository.MonitoramentoDiarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MonitoramentoServiceTest {

    @Autowired
    private MonitoramentoService service;

    @Autowired
    private MonitoramentoDiarioRepository registros;

    @Test
    void primeiroHeartbeatNaoIncrementaNada() {
        OffsetDateTime t0 = OffsetDateTime.of(2026, 8, 29, 8, 0, 0, 0, ZoneOffset.UTC);

        service.registrar("demo:CAM-MS1", "demo", t0);

        var registro = registros.findByCameraIdAndDiaAndTurno("demo:CAM-MS1", t0.toLocalDate(), Turno.MANHA)
                .orElseThrow();
        assertThat(registro.getSegundosMonitorados()).isZero();
    }

    @Test
    void segundoHeartbeatDentroDoIntervaloNormalIncrementaOGapExato() {
        OffsetDateTime t0 = OffsetDateTime.of(2026, 8, 29, 8, 0, 0, 0, ZoneOffset.UTC);
        service.registrar("demo:CAM-MS2", "demo", t0);

        service.registrar("demo:CAM-MS2", "demo", t0.plusSeconds(30));

        var registro = registros.findByCameraIdAndDiaAndTurno("demo:CAM-MS2", t0.toLocalDate(), Turno.MANHA)
                .orElseThrow();
        assertThat(registro.getSegundosMonitorados()).isEqualTo(30);
    }

    @Test
    void gapLongoEhLimitadoAoTeto() {
        OffsetDateTime t0 = OffsetDateTime.of(2026, 8, 29, 8, 0, 0, 0, ZoneOffset.UTC);
        service.registrar("demo:CAM-MS3", "demo", t0);

        // Pipeline caiu e voltou 3 horas depois — bem acima do teto default (60s).
        service.registrar("demo:CAM-MS3", "demo", t0.plusHours(3));

        var registro = registros.findByCameraIdAndDiaAndTurno("demo:CAM-MS3", t0.toLocalDate(), Turno.MANHA)
                .orElseThrow();
        assertThat(registro.getSegundosMonitorados()).isEqualTo(60);
    }

    @Test
    void heartbeatsEmTurnosDiferentesGeramLinhasSeparadas() {
        OffsetDateTime manha = OffsetDateTime.of(2026, 8, 29, 8, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime tarde = OffsetDateTime.of(2026, 8, 29, 15, 0, 0, 0, ZoneOffset.UTC);

        service.registrar("demo:CAM-MS4", "demo", manha);
        service.registrar("demo:CAM-MS4", "demo", tarde);

        assertThat(registros.findByCameraIdAndDiaAndTurno("demo:CAM-MS4", manha.toLocalDate(), Turno.MANHA))
                .isPresent();
        assertThat(registros.findByCameraIdAndDiaAndTurno("demo:CAM-MS4", tarde.toLocalDate(), Turno.TARDE))
                .isPresent();
    }
}
