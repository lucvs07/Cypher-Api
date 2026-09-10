package com.dev4.cypher.repository;

import com.dev4.cypher.model.AlertaLeitura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertaLeituraRepository extends JpaRepository<AlertaLeitura, Long> {

    boolean existsByEmpresaIdAndUsuarioAndAlertaId(String empresaId, String usuario, Long alertaId);

    List<AlertaLeitura> findByEmpresaIdAndUsuarioAndAlertaIdIn(String empresaId, String usuario, List<Long> alertaIds);
}
