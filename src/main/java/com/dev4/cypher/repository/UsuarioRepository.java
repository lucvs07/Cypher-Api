package com.dev4.cypher.repository;

import com.dev4.cypher.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    List<Usuario> findByEmpresaId(String empresaId);

    boolean existsByUsername(String username);

    void deleteByUsername(String username);

    Optional<Usuario> findByUsernameAndEmpresaId(String username, String empresaId);

    boolean existsByUsernameAndEmpresaId(String username, String empresaId);

    void deleteByUsernameAndEmpresaId(String username, String empresaId);
}
