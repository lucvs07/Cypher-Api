package com.dev4.cypher.service;

import com.dev4.cypher.model.MonitoramentoDiario;
import com.dev4.cypher.model.Turno;
import com.dev4.cypher.repository.MonitoramentoDiarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

/** Acumula tempo monitorado a cada heartbeat (spec §2.2). */
@Service
public class MonitoramentoService {

    private final MonitoramentoDiarioRepository registros;
    private final long maxGapSegundos;

    public MonitoramentoService(MonitoramentoDiarioRepository registros,
                                @Value("${cypher.heartbeat.max-gap-seconds:60}") long maxGapSegundos) {
        this.registros = registros;
        this.maxGapSegundos = maxGapSegundos;
    }

    @Transactional
    public void registrar(String cameraId, String empresaId, OffsetDateTime agora) {
        Turno turno = TurnoWindow.turnoDe(agora);
        MonitoramentoDiario registro = registros
                .findByCameraIdAndDiaAndTurno(cameraId, agora.toLocalDate(), turno)
                .orElseGet(() -> new MonitoramentoDiario(null, cameraId, empresaId,
                        agora.toLocalDate(), turno, 0L, agora));

        if (registro.getId() != null) {
            // Registro ja existia: ha um heartbeat anterior para comparar.
            long desdeUltimo = Duration.between(registro.getUltimoHeartbeat(), agora).getSeconds();
            long incremento = Math.max(0, Math.min(desdeUltimo, maxGapSegundos));
            registro.setSegundosMonitorados(registro.getSegundosMonitorados() + incremento);
        }
        registro.setUltimoHeartbeat(agora);
        registros.saveAndFlush(registro);
    }
}
