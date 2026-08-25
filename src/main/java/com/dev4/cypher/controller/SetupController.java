package com.dev4.cypher.controller;

import com.dev4.cypher.dto.AuthResponse;
import com.dev4.cypher.dto.SetupRequest;
import com.dev4.cypher.dto.SetupStatusResponse;
import com.dev4.cypher.model.Usuario;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Setup de execução única do gestor mestre (spec E4 §2.5) — substitui o seed
 * estático de `application.yaml` descartado na revisão de design. `POST
 * /setup` reaproveita a mesma validação/hash (`PasswordEncoder`, Tarefa 1) e
 * o mesmo mecanismo de sessão (`SessionAuthenticator`, desta tarefa) de
 * `AuthController.login` — a diferença é só a pré-condição (tabela vazia) e
 * o efeito colateral (cria em vez de autenticar um `Usuario` existente).
 *
 * <p>A spec (E4 §2.5) sugere `UsuarioService.criarPrimeiroGestor(...)` como
 * implementação — não seguido aqui de propósito: `UsuarioService` só existe
 * a partir da Tarefa 10, e este controller precisa compilar já na Tarefa 7.
 * Em vez disso, `SetupController` acessa `UsuarioRepository`/`PasswordEncoder`
 * direto, exatamente como `AuthController` já faz (nenhum dos dois tem
 * service layer) — a "lógica não duplicada" que a spec pede é o
 * `SessionAuthenticator` compartilhado, não uma dependência de
 * `UsuarioService` que criaria um ciclo de tarefas.
 *
 * <p>`@Transactional` no método (não na classe, e sem service dedicado)
 * cobre a janela entre checar `usuarios.count()` e gravar o novo `Usuario` —
 * mesmo padrão de `@Transactional` em método de escrita usado por
 * `CatalogService` (Tarefa 4/5), só que aqui, sem service layer, a anotação
 * fica no próprio controller.
 */
@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final SessionAuthenticator sessionAuthenticator;

    public SetupController(UsuarioRepository usuarios, PasswordEncoder encoder,
                           SessionAuthenticator sessionAuthenticator) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.sessionAuthenticator = sessionAuthenticator;
    }

    @GetMapping("/status")
    public SetupStatusResponse status() {
        return new SetupStatusResponse(usuarios.count() > 0);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> setup(@Valid @RequestBody SetupRequest req,
                                   HttpServletRequest request, HttpServletResponse response) {
        if (usuarios.count() > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", "Setup ja concluido"));
        }
        Usuario gestor = usuarios.saveAndFlush(
                new Usuario(null, req.username(), encoder.encode(req.password()), "GESTOR", null));
        sessionAuthenticator.autenticar(new GestorAuthentication(gestor.getUsername()), request, response);
        return ResponseEntity.ok(AuthResponse.gestor(gestor.getUsername()));
    }
}
