package com.dev4.cypher.service;

import com.dev4.cypher.catalog.CatalogValidationException;
import com.dev4.cypher.dto.UserRequest;
import com.dev4.cypher.dto.UsuarioResponse;
import com.dev4.cypher.model.Usuario;
import com.dev4.cypher.repository.EstacaoRepository;
import com.dev4.cypher.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * CRUD de usuário — GESTOR (sem estação) ou OPERADOR (vinculado a uma
 * estação, uma segunda forma de entrar além do token — revisão pós-E4).
 */
@Service
public class UsuarioService {

    private static final Set<String> PAPEIS_VALIDOS = Set.of("GESTOR", "OPERADOR");

    private final UsuarioRepository usuarios;
    private final EstacaoRepository estacoes;
    private final PasswordEncoder encoder;

    public UsuarioService(UsuarioRepository usuarios, EstacaoRepository estacoes, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.estacoes = estacoes;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarios.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public UsuarioResponse criar(UserRequest req) {
        validarPapelEEstacao(req.papel(), req.estacaoId());
        if (usuarios.existsByUsername(req.username())) {
            throw new CatalogValidationException(HttpStatus.CONFLICT,
                    "Usuario ja cadastrado: " + req.username());
        }
        if (req.password() == null || req.password().isBlank()) {
            throw new CatalogValidationException(HttpStatus.BAD_REQUEST,
                    "password e obrigatoria no cadastro");
        }
        String estacaoId = "OPERADOR".equals(req.papel()) ? req.estacaoId() : null;
        Usuario novo = new Usuario(null, req.username(), encoder.encode(req.password()), req.papel(), estacaoId);
        return toResponse(usuarios.saveAndFlush(novo));
    }

    @Transactional
    public Optional<UsuarioResponse> atualizar(String username, UserRequest req) {
        validarPapelEEstacao(req.papel(), req.estacaoId());
        return usuarios.findByUsername(username).map(usuario -> {
            if (!usuario.getUsername().equals(req.username()) && usuarios.existsByUsername(req.username())) {
                throw new CatalogValidationException(HttpStatus.CONFLICT,
                        "Usuario ja cadastrado: " + req.username());
            }
            usuario.setUsername(req.username());
            usuario.setPapel(req.papel());
            usuario.setEstacaoId("OPERADOR".equals(req.papel()) ? req.estacaoId() : null);
            // password ausente/vazia = nao redefinir (mesmo padrao de StationRequest.ativa == null).
            if (req.password() != null && !req.password().isBlank()) {
                usuario.setPasswordHash(encoder.encode(req.password()));
            }
            return toResponse(usuarios.saveAndFlush(usuario));
        });
    }

    @Transactional
    public boolean remover(String username) {
        if (!usuarios.existsByUsername(username)) {
            return false;
        }
        usuarios.deleteByUsername(username);
        return true;
    }

    private void validarPapelEEstacao(String papel, String estacaoId) {
        if (!PAPEIS_VALIDOS.contains(papel)) {
            throw new CatalogValidationException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "papel deve ser GESTOR ou OPERADOR: " + papel);
        }
        if ("OPERADOR".equals(papel)) {
            if (estacaoId == null || estacaoId.isBlank()) {
                throw new CatalogValidationException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "estacaoId e obrigatorio para OPERADOR");
            }
            if (!estacoes.existsById(estacaoId)) {
                throw new CatalogValidationException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "estacao inexistente: " + estacaoId);
            }
        }
    }

    private UsuarioResponse toResponse(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getUsername(), u.getPapel(), u.getEstacaoId());
    }
}
