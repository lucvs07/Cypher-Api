package com.dev4.cypher.controller;

import com.dev4.cypher.dto.AuthResponse;
import com.dev4.cypher.dto.SetupRequest;
import com.dev4.cypher.model.Empresa;
import com.dev4.cypher.model.Usuario;
import com.dev4.cypher.repository.EmpresaRepository;
import com.dev4.cypher.repository.UsuarioRepository;
import com.dev4.cypher.security.GestorAuthentication;
import com.dev4.cypher.security.SessionAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Sign-up de uma empresa nova: cria a `Empresa` e o `Usuario` mestre dela
 * (spec §3, §7). O conflito passa a ser por slug de empresa, nunca mais
 * global — duas empresas podem se cadastrar em paralelo sem se afetar.
 */
@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {

    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final SessionAuthenticator sessionAuthenticator;

    public SetupController(EmpresaRepository empresas, UsuarioRepository usuarios,
                           PasswordEncoder encoder, SessionAuthenticator sessionAuthenticator) {
        this.empresas = empresas;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.sessionAuthenticator = sessionAuthenticator;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> setup(@Valid @RequestBody SetupRequest req,
                                   HttpServletRequest request, HttpServletResponse response) {
        if (empresas.existsById(req.empresaId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("erro", "Empresa ja cadastrada: " + req.empresaId()));
        }
        empresas.saveAndFlush(new Empresa(req.empresaId(), req.empresaNome(), true));

        String username = req.username() + "@" + req.empresaId();
        Usuario gestor = usuarios.saveAndFlush(
                new Usuario(null, username, encoder.encode(req.password()), "GESTOR", null, req.empresaId()));
        sessionAuthenticator.autenticar(
                new GestorAuthentication(gestor.getUsername(), gestor.getEmpresaId()), request, response);
        return ResponseEntity.ok(AuthResponse.gestor(gestor.getUsername(), req.empresaId(), req.empresaNome()));
    }
}
