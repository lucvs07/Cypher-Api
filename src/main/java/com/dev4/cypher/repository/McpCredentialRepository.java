package com.dev4.cypher.repository;

import com.dev4.cypher.model.McpCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface McpCredentialRepository extends JpaRepository<McpCredential, Long> {

    List<McpCredential> findByEmpresaId(String empresaId);

    List<McpCredential> findByEmpresaIdAndAtivaTrue(String empresaId);

    Optional<McpCredential> findByEmpresaIdAndNome(String empresaId, String nome);
}
