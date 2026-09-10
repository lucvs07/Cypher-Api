package com.dev4.cypher.repository;

import com.dev4.cypher.model.Alerta;
import com.dev4.cypher.model.AlertaStatus;
import com.dev4.cypher.model.AlertaTipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    Optional<Alerta> findByIncidentKey(String incidentKey);

    List<Alerta> findByEmpresaId(String empresaId);

    List<Alerta> findByEmpresaIdAndCameraIdInAndDataHoraBetween(
            String empresaId, List<String> cameraIds, OffsetDateTime inicio, OffsetDateTime fim);

    List<Alerta> findByEmpresaIdAndTipoAndCameraIdInAndDataHoraAfter(
            String empresaId, AlertaTipo tipo, List<String> cameraIds, OffsetDateTime limite);

    /** Busca por id restrita ao tenant atual — um id de outro tenant vem vazio (achado de segurança pós-Tarefa 5). */
    Optional<Alerta> findByIdAndEmpresaId(Long id, String empresaId);

    /** Batch de {@link #findByIdAndEmpresaId} — filtra de uma vez quais ids pertencem à empresa (revisão final da Tarefa 3). */
    List<Alerta> findAllByIdInAndEmpresaId(List<Long> ids, String empresaId);

    /**
     * Busca com filtros opcionais. Cada parâmetro nulo desliga seu próprio
     * predicado, o que evita quatro métodos derivados quase idênticos.
     *
     * <p>{@code :estacaoId} e {@code :cameraId} chegam já com o prefixo do
     * tenant ({@code demo:EST-01}/{@code demo:CAM-01}), assim como
     * {@code Camera.id} e, desde a Tarefa 5, {@code Alerta.cameraId} — o
     * subselect compara {@code c.id} diretamente, sem remover prefixo.
     */
    @Query("""
            SELECT a FROM Alerta a
            WHERE a.empresaId = :empresaId
              AND (:since IS NULL OR a.dataHora >= :since)
              AND (:cameraId IS NULL OR a.cameraId = :cameraId)
              AND (:severityMin IS NULL OR a.severidade >= :severityMin)
              AND (:status IS NULL OR a.status = :status)
              AND (:estacaoId IS NULL OR a.cameraId IN
                    (SELECT c.id FROM Camera c WHERE c.estacaoId = :estacaoId))
            """)
    Page<Alerta> buscar(@Param("empresaId") String empresaId,
                        @Param("since") OffsetDateTime since,
                        @Param("cameraId") String cameraId,
                        @Param("severityMin") Integer severityMin,
                        @Param("status") AlertaStatus status,
                        @Param("estacaoId") String estacaoId,
                        Pageable pageable);
}
