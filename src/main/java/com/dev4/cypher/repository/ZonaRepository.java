package com.dev4.cypher.repository;

import com.dev4.cypher.model.Zona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZonaRepository extends JpaRepository<Zona, Long> {

    List<Zona> findByCameraIdAndEmpresaId(String cameraId, String empresaId);

    List<Zona> findByCameraIdAndEmpresaIdAndAtivaTrue(String cameraId, String empresaId);
}
