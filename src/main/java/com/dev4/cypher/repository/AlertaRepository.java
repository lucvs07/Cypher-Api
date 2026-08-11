package com.dev4.cypher.repository;

import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.model.AlertaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    Optional<Alerta> findByIncidentKey(String incidentKey);

    /**
     * Busca com filtros opcionais. Cada parâmetro nulo desliga seu próprio
     * predicado, o que evita quatro métodos derivados quase idênticos.
     */
    @Query("""
            SELECT a FROM Alerta a
            WHERE (:since IS NULL OR a.dataHora >= :since)
              AND (:cameraId IS NULL OR a.cameraId = :cameraId)
              AND (:severityMin IS NULL OR a.severidade >= :severityMin)
              AND (:status IS NULL OR a.status = :status)
            """)
    Page<Alerta> buscar(@Param("since") OffsetDateTime since,
                        @Param("cameraId") String cameraId,
                        @Param("severityMin") Integer severityMin,
                        @Param("status") AlertaStatus status,
                        Pageable pageable);
}
