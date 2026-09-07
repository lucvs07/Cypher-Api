package com.dev4.cypher.repository;

import com.dev4.cypher.model.MonitoramentoDiario;
import com.dev4.cypher.model.Turno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MonitoramentoDiarioRepository extends JpaRepository<MonitoramentoDiario, Long> {

    Optional<MonitoramentoDiario> findByCameraIdAndDiaAndTurno(String cameraId, LocalDate dia, Turno turno);

    // Restrito ao turno da equipe (spec §2.3) — MonitoramentoDiario é uma linha
    // por (camera, dia, turno); somar os três turnos daria até 300% de cobertura.
    List<MonitoramentoDiario> findByEmpresaIdAndCameraIdInAndTurnoAndDiaBetween(
            String empresaId, List<String> cameraIds, Turno turno, LocalDate inicio, LocalDate fim);
}
