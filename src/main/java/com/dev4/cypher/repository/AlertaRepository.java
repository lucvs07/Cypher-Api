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
     *
     * <p>{@code :estacaoId} chega já com o prefixo do tenant ({@code demo:EST-01});
     * {@code Camera.id} também é prefixado, mas {@code Alerta.cameraId} ainda guarda
     * o código puro nesta fase (a Tarefa 5 é quem o prefixa). Até lá, o subselect
     * remove o prefixo de {@code c.id} para casar com {@code a.cameraId} — mesma
     * operação de {@code TenantContext.codigo}, aqui em JPQL. A Tarefa 5 volta
     * este subselect para {@code SELECT c.id}.
     */
    @Query("""
            SELECT a FROM Alerta a
            WHERE (:since IS NULL OR a.dataHora >= :since)
              AND (:cameraId IS NULL OR a.cameraId = :cameraId)
              AND (:severityMin IS NULL OR a.severidade >= :severityMin)
              AND (:status IS NULL OR a.status = :status)
              AND (:estacaoId IS NULL OR a.cameraId IN
                    (SELECT SUBSTRING(c.id, LOCATE(':', c.id) + 1) FROM Camera c WHERE c.estacaoId = :estacaoId))
            """)
    Page<Alerta> buscar(@Param("since") OffsetDateTime since,
                        @Param("cameraId") String cameraId,
                        @Param("severityMin") Integer severityMin,
                        @Param("status") AlertaStatus status,
                        @Param("estacaoId") String estacaoId,
                        Pageable pageable);
}
