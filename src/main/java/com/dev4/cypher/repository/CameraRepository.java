package com.dev4.cypher.repository;

import com.dev4.cypher.model.Camera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CameraRepository extends JpaRepository<Camera, String> {

    List<Camera> findByEstacaoId(String estacaoId);

    List<Camera> findByAtivaTrue();

    List<Camera> findByEstacaoIdAndAtivaTrue(String estacaoId);

    List<Camera> findByEmpresaId(String empresaId);

    List<Camera> findByEmpresaIdAndAtivaTrue(String empresaId);

    Optional<Camera> findByEmpresaIdAndStreamUrl(String empresaId, String streamUrl);

    Optional<Camera> findByApiKey(String apiKey);
}
