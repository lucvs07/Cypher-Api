package com.dev4.cypher.service;

import com.dev4.cypher.catalog.CatalogValidationException;
import com.dev4.cypher.dto.McpCredentialCriadaResponse;
import com.dev4.cypher.dto.McpCredentialRequest;
import com.dev4.cypher.dto.McpCredentialResponse;
import com.dev4.cypher.model.McpCredential;
import com.dev4.cypher.repository.McpCredentialRepository;
import com.dev4.cypher.security.TenantContext;
import com.dev4.cypher.security.TokenGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** CRUD de credenciais de acesso ao servidor MCP, escopado por empresa. */
@Service
public class McpCredentialService {

    private final McpCredentialRepository credenciais;
    private final PasswordEncoder encoder;

    public McpCredentialService(McpCredentialRepository credenciais, PasswordEncoder encoder) {
        this.credenciais = credenciais;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<McpCredentialResponse> listar(boolean incluirInativas) {
        String empresaId = TenantContext.currentEmpresaId();
        List<McpCredential> lista = incluirInativas
                ? credenciais.findByEmpresaId(empresaId)
                : credenciais.findByEmpresaIdAndAtivaTrue(empresaId);
        return lista.stream().map(this::toResponse).toList();
    }

    @Transactional
    public McpCredentialCriadaResponse criar(McpCredentialRequest req) {
        String empresaId = TenantContext.currentEmpresaId();
        exigirNomeLivre(empresaId, req.nome());

        String secretPlano = TokenGenerator.novo("mcp-key");
        OffsetDateTime agora = OffsetDateTime.now();
        McpCredential nova = new McpCredential(null, req.nome(), encoder.encode(secretPlano),
                true, empresaId, agora, null);
        McpCredential salva = credenciais.saveAndFlush(nova);

        return new McpCredentialCriadaResponse(salva.getId(), salva.getNome(), salva.getCriadaEm(),
                salva.isAtiva(), salva.getId() + "." + secretPlano);
    }

    @Transactional
    public boolean revogar(Long id) {
        String empresaId = TenantContext.currentEmpresaId();
        return credenciais.findById(id)
                .filter(c -> c.getEmpresaId().equals(empresaId))
                .map(c -> {
                    c.setAtiva(false);
                    credenciais.saveAndFlush(c);
                    return true;
                }).orElse(false);
    }

    private void exigirNomeLivre(String empresaId, String nome) {
        Optional<McpCredential> existente = credenciais.findByEmpresaIdAndNome(empresaId, nome);
        if (existente.isPresent()) {
            throw new CatalogValidationException(HttpStatus.CONFLICT,
                    "Ja existe uma credencial MCP com esse nome: " + nome);
        }
    }

    private McpCredentialResponse toResponse(McpCredential c) {
        return new McpCredentialResponse(c.getId(), c.getNome(), c.getCriadaEm(),
                c.getUltimoUsoEm(), c.isAtiva());
    }
}
