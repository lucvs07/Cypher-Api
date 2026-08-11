package com.dev4.cypher.repository;

import com.dev4.cypher.model.Camera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CameraRepository extends JpaRepository<Camera, String> {

    List<Camera> findByEstacaoId(String estacaoId);
}
