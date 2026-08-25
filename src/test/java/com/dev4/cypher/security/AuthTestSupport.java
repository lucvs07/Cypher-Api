package com.dev4.cypher.security;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login real via MockMvc para os testes de endpoint protegido — nunca um
 * Authentication ou sessão forjados, para que o teste atravesse a mesma
 * cadeia de filtros que um cliente real atravessaria.
 */
public final class AuthTestSupport {

    /**
     * Credencial fixa de teste do gestor bootstrap. Sem seeder (revisão de
     * design E4 §2.5, Tarefa 2): quem cria esse usuário é
     * {@link #loginGestorBootstrap}, chamando `POST /api/v1/setup` — o
     * mesmo endpoint que o onboarding do front chama uma única vez.
     */
    public static final String GESTOR_USERNAME = "admin";
    public static final String GESTOR_PASSWORD = "admin123";

    private AuthTestSupport() {
    }

    /**
     * Auto-provisionante: tenta criar o gestor bootstrap via `POST /setup`
     * (que já devolve sessão autenticada, Tarefa 7). Se a tabela `usuarios`
     * já não estiver mais vazia — outra classe de teste, no mesmo contexto
     * `@SpringBootTest` compartilhado (H2 com `DB_CLOSE_DELAY=-1`), já
     * chamou este método antes —, `/setup` devolve 409 e este método cai
     * para `POST /auth/login` com a mesma credencial fixa. Isso só é seguro
     * porque `admin`/`GESTOR_USERNAME` é o ÚNICO username que qualquer
     * caminho deste plano usa para o "primeiro" `Usuario` de um contexto
     * (nenhuma outra classe cria um `Usuario` sem antes chamar este
     * método) — ver `AuthControllerApiTest`, que documenta a mesma garantia
     * do lado do teste que exercita `POST /auth/login` diretamente.
     */
    public static MockHttpSession loginGestorBootstrap(MockMvc mvc) throws Exception {
        MockHttpSession session = new MockHttpSession();
        int status = mvc.perform(post("/api/v1/setup").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}"
                                .formatted(GESTOR_USERNAME, GESTOR_PASSWORD)))
                .andReturn().getResponse().getStatus();
        if (status != 200 && status != 409) {
            // Nem criou (200) nem já existia (409): algo além do esperado
            // aconteceu em /setup (400 de validação, 500, etc.) — falhar aqui,
            // alto e claro, em vez de deixar o fallthrough devolver uma sessão
            // não autenticada que só se manifestaria como um 401 confuso em
            // algum teste de outra classe, bem mais tarde e longe da causa real.
            throw new IllegalStateException(
                    "POST /api/v1/setup devolveu status inesperado " + status
                            + " (esperado 200 ou 409) — loginGestorBootstrap não pode prosseguir");
        }
        if (status == 409) {
            return loginGestor(mvc, GESTOR_USERNAME, GESTOR_PASSWORD);
        }
        return session;
    }

    public static MockHttpSession loginGestor(MockMvc mvc, String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/v1/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)))
                .andExpect(status().isOk());
        return session;
    }

    public static MockHttpSession loginOperador(MockMvc mvc, String token) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/v1/auth/login-estacao").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(token)))
                .andExpect(status().isOk());
        return session;
    }
}
