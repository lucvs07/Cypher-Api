package com.dev4.cypher.service;

import com.dev4.cypher.catalog.CatalogValidationException;
import com.dev4.cypher.dto.UserRequest;
import com.dev4.cypher.dto.UsuarioResponse;
import com.dev4.cypher.model.Usuario;
import com.dev4.cypher.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** CRUD de usuário (sempre GESTOR — spec E4 §2.5). */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public UsuarioService(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarios.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public UsuarioResponse criar(UserRequest req) {
        exigirPapelGestor(req.papel());
        if (usuarios.existsByUsername(req.username())) {
            throw new CatalogValidationException(HttpStatus.CONFLICT,
                    "Usuario ja cadastrado: " + req.username());
        }
        if (req.password() == null || req.password().isBlank()) {
            throw new CatalogValidationException(HttpStatus.BAD_REQUEST,
                    "password e obrigatoria no cadastro");
        }
        Usuario novo = new Usuario(null, req.username(), encoder.encode(req.password()));
        return toResponse(usuarios.saveAndFlush(novo));
    }

    @Transactional
    public Optional<UsuarioResponse> atualizar(Long id, UserRequest req) {
        exigirPapelGestor(req.papel());
        return usuarios.findById(id).map(usuario -> {
            if (!usuario.getUsername().equals(req.username()) && usuarios.existsByUsername(req.username())) {
                throw new CatalogValidationException(HttpStatus.CONFLICT,
                        "Usuario ja cadastrado: " + req.username());
            }
            usuario.setUsername(req.username());
            // password ausente/vazia = nao redefinir (mesmo padrao de StationRequest.ativa == null).
            if (req.password() != null && !req.password().isBlank()) {
                usuario.setPasswordHash(encoder.encode(req.password()));
            }
            return toResponse(usuarios.saveAndFlush(usuario));
        });
    }

    @Transactional
    public boolean remover(Long id) {
        if (!usuarios.existsById(id)) {
            return false;
        }
        usuarios.deleteById(id);
        return true;
    }

    private void exigirPapelGestor(String papel) {
        if (!"GESTOR".equals(papel)) {
            throw new CatalogValidationException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "papel deve ser GESTOR: " + papel);
        }
    }

    private UsuarioResponse toResponse(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getUsername(), "GESTOR");
    }
}
