package com.dev4.cypher.repository;

import com.dev4.cypher.model.IncidentLlmAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IncidentLlmAnalysisRepository extends JpaRepository<IncidentLlmAnalysis, Long> {

    Optional<IncidentLlmAnalysis> findFirstByAlertaIdOrderByCriadoEmDesc(Long alertaId);
}
