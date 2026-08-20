package com.dev4.cypher.repository;

import com.dev4.cypher.model.Estacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EstacaoRepository extends JpaRepository<Estacao, String> {

    List<Estacao> findByAtivaTrue();
}
