package com.dev4.cypher.repository;

import com.dev4.cypher.model.Estacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EstacaoRepository extends JpaRepository<Estacao, String> {

    List<Estacao> findByAtivaTrue();

    List<Estacao> findByEmpresaId(String empresaId);

    List<Estacao> findByEmpresaIdAndAtivaTrue(String empresaId);

    Optional<Estacao> findByToken(String token);
}
