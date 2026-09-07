package com.dev4.cypher.repository;

import com.dev4.cypher.model.Equipe;
import com.dev4.cypher.model.Turno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EquipeRepository extends JpaRepository<Equipe, String> {

    List<Equipe> findByAtivaTrue();

    List<Equipe> findByTurno(Turno turno);

    List<Equipe> findByEmpresaId(String empresaId);

    List<Equipe> findByEmpresaIdAndAtivaTrue(String empresaId);

    List<Equipe> findByEmpresaIdAndTurno(String empresaId, Turno turno);
}
