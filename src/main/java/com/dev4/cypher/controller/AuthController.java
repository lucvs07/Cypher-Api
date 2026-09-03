package com.dev4.cypher.controller;

import com.dev4.cypher.dto.AuthResponse;
import com.dev4.cypher.dto.LoginEstacaoRequest;
import com.dev4.cypher.dto.LoginRequest;
import com.dev4.cypher.model.Empresa;
import com.dev4.cypher.model.Estacao;
import com.dev4.cypher.model.Usuario;
import com.dev4.cypher.repository.EmpresaRepository;
import com.dev4.cypher.repository.EstacaoRepository;
import com.dev4.cypher.repository.UsuarioRepository;
import com.dev4.cypher.security.GestorAuthentication;
import com.dev4.cypher.security.OperadorAuthentication;
import com.dev4.cypher.security.SessionAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Login/logout/quem-sou-eu para os dois papéis autenticados por sessão (spec E4 §2.2). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UsuarioRepository usuarios;
    private final EstacaoRepository estacoes;
    private final EmpresaRepository empresas;
    private final PasswordEncoder encoder;
    private final SessionAuthenticator sessionAuthenticator;

    public AuthController(UsuarioRepository usuarios, EstacaoRepository estacoes, EmpresaRepository empresas,
                          PasswordEncoder encoder, SessionAuthenticator sessionAuthenticator) {
        this.usuarios = usuarios;
        this.estacoes = estacoes;
        this.empresas = empresas;
        this.encoder = encoder;
        this.sessionAuthenticator = sessionAuthenticator;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                   HttpServletRequest request, HttpServletResponse response) {
        Usuario usuario = usuarios.findByUsername(req.username()).orElse(null);
        if (usuario == null || !encoder.matches(req.password(), usuario.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("erro", "Usuario ou senha invalidos"));
        }
        String empresaNome = nomeDaEmpresa(usuario.getEmpresaId());
        if ("OPERADOR".equals(usuario.getPapel())) {
            sessionAuthenticator.autenticar(
                    new OperadorAuthentication(usuario.getEstacaoId(), usuario.getUsername(), usuario.getEmpresaId()),
                    request, response);
            return ResponseEntity.ok(AuthResponse.operador(
                    com.dev4.cypher.security.TenantContext.codigo(usuario.getEstacaoId()),
                    usuario.getUsername(), usuario.getEmpresaId(), empresaNome));
        }
        sessionAuthenticator.autenticar(new GestorAuthentication(usuario.getUsername(), usuario.getEmpresaId()),
                request, response);
        return ResponseEntity.ok(AuthResponse.gestor(usuario.getUsername(), usuario.getEmpresaId(), empresaNome));
    }

    @PostMapping("/login-estacao")
    public ResponseEntity<?> loginEstacao(@Valid @RequestBody LoginEstacaoRequest req,
                                          HttpServletRequest request, HttpServletResponse response) {
        Estacao estacao = estacoes.findByToken(req.token()).orElse(null);
        if (estacao == null || !estacao.isAtiva()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("erro", "Token de estacao invalido ou inativo"));
        }
        sessionAuthenticator.autenticar(
                new OperadorAuthentication(estacao.getId(), estacao.getEmpresaId()), request, response);
        return ResponseEntity.ok(AuthResponse.operador(
                com.dev4.cypher.security.TenantContext.codigo(estacao.getId()),
                estacao.getEmpresaId(), nomeDaEmpresa(estacao.getEmpresaId())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof GestorAuthentication g) {
            return ResponseEntity.ok(AuthResponse.gestor(g.getUsername(), g.getEmpresaId(), nomeDaEmpresa(g.getEmpresaId())));
        }
        if (auth instanceof OperadorAuthentication o) {
            String estacaoId = com.dev4.cypher.security.TenantContext.codigo(o.getEstacaoId());
            String empresaNome = nomeDaEmpresa(o.getEmpresaId());
            return ResponseEntity.ok(o.getUsername() != null
                    ? AuthResponse.operador(estacaoId, o.getUsername(), o.getEmpresaId(), empresaNome)
                    : AuthResponse.operador(estacaoId, o.getEmpresaId(), empresaNome));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    private String nomeDaEmpresa(String empresaId) {
        return empresas.findById(empresaId).map(Empresa::getNome).orElse(null);
    }
}
