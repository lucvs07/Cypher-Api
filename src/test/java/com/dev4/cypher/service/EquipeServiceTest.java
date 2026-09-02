package com.dev4.cypher.service;

import com.dev4.cypher.catalog.CatalogValidationException;
import com.dev4.cypher.dto.TeamRequest;
import com.dev4.cypher.dto.TeamResponse;
import com.dev4.cypher.model.Estacao;
import com.dev4.cypher.model.Turno;
import com.dev4.cypher.repository.EquipeRepository;
import com.dev4.cypher.repository.EstacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class EquipeServiceTest {

    @Autowired
    private EquipeRepository equipes;

    @Autowired
    private EstacaoRepository estacoes;

    private EquipeService service;

    @BeforeEach
    void setUp() {
        service = new EquipeService(equipes, estacoes);
        estacoes.save(new Estacao("demo:EST-01", "Esteira", "montagem", true, "tok-EST-01", "demo"));
        estacoes.saveAndFlush(new Estacao("demo:EST-02", "Prensa", "estamparia", true, "tok-EST-02", "demo"));
    }

    private TeamRequest equipeA() {
        return new TeamRequest("EQ-01", "Equipe A", Turno.MANHA, List.of("EST-01"), null);
    }

    @Test
    void cadastraEquipeAtivaPorDefault() {
        TeamResponse criada = service.criar(equipeA());

        assertThat(criada.id()).isEqualTo("EQ-01");
        assertThat(criada.turno()).isEqualTo(Turno.MANHA);
        assertThat(criada.estacaoIds()).containsExactly("EST-01");
        assertThat(criada.ativa()).isTrue();
    }

    @Test
    void recusaCodigoDuplicado() {
        service.criar(equipeA());

        assertThatThrownBy(() -> service.criar(equipeA()))
                .isInstanceOf(CatalogValidationException.class)
                .satisfies(e -> assertThat(((CatalogValidationException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void recusaEstacaoInexistente() {
        assertThatThrownBy(() -> service.criar(
                new TeamRequest("EQ-01", "Equipe A", Turno.MANHA, List.of("EST-99"), null)))
                .isInstanceOf(CatalogValidationException.class)
                .satisfies(e -> assertThat(((CatalogValidationException) e).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void recusaConflitoDeEstacaoNoMesmoTurno() {
        service.criar(equipeA());

        assertThatThrownBy(() -> service.criar(
                new TeamRequest("EQ-02", "Equipe B", Turno.MANHA, List.of("EST-01"), null)))
                .isInstanceOf(CatalogValidationException.class)
                .satisfies(e -> assertThat(((CatalogValidationException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void permiteMesmaEstacaoEmTurnosDiferentes() {
        service.criar(equipeA());

        TeamResponse tarde = service.criar(
                new TeamRequest("EQ-02", "Equipe B", Turno.TARDE, List.of("EST-01"), null));

        assertThat(tarde.turno()).isEqualTo(Turno.TARDE);
    }

    @Test
    void editaNomeEEstacoes() {
        service.criar(equipeA());

        Optional<TeamResponse> editada = service.atualizar("EQ-01",
                new TeamRequest("EQ-01", "Equipe A e B", Turno.MANHA, List.of("EST-01", "EST-02"), null));

        assertThat(editada).isPresent();
        assertThat(editada.get().nome()).isEqualTo("Equipe A e B");
        assertThat(editada.get().estacaoIds()).containsExactlyInAnyOrder("EST-01", "EST-02");
    }

    @Test
    void recusaRenomearOCodigo() {
        service.criar(equipeA());

        assertThatThrownBy(() -> service.atualizar("EQ-01",
                new TeamRequest("EQ-99", "Equipe A", Turno.MANHA, List.of("EST-01"), null)))
                .isInstanceOf(CatalogValidationException.class);
    }

    @Test
    void editarInexistenteDevolveVazio() {
        assertThat(service.atualizar("EQ-99",
                new TeamRequest("EQ-99", "X", Turno.MANHA, List.of("EST-01"), null))).isEmpty();
    }

    @Test
    void removeEquipe() {
        service.criar(equipeA());

        assertThat(service.remover("EQ-01")).isTrue();
        assertThat(equipes.findById("demo:EQ-01")).isEmpty();
    }

    @Test
    void removerInexistenteDevolveFalso() {
        assertThat(service.remover("EQ-99")).isFalse();
    }

    @Test
    void estacaoIdsDaRespostaEUmaCopiaDefensivaDesconectadaDoHibernate() {
        TeamResponse criada = service.criar(equipeA());

        // toResponse() deve devolver List.copyOf(...), nao a colecao gerenciada pelo
        // Hibernate (ex.: org.hibernate.collection.spi.PersistentBag). Se voltasse a
        // referencia viva, a lista so "funcionaria" enquanto a sessao do Hibernate
        // estivesse aberta (open-in-view=true); com OSIV desligado isso lancaria
        // LazyInitializationException ao ser serializada fora da transacao.
        assertThat(criada.estacaoIds().getClass().getName())
                .as("estacaoIds nao deve ser uma colecao gerenciada pelo Hibernate")
                .doesNotContain("org.hibernate");
        assertThatThrownBy(() -> criada.estacaoIds().add("EST-02"))
                .as("copia defensiva deve ser imutavel")
                .isInstanceOf(UnsupportedOperationException.class);

        Optional<TeamResponse> editada = service.atualizar("EQ-01", equipeA());
        assertThat(editada).isPresent();
        assertThat(editada.get().estacaoIds().getClass().getName()).doesNotContain("org.hibernate");

        TeamResponse listada = service.listar().get(0);
        assertThat(listada.estacaoIds().getClass().getName()).doesNotContain("org.hibernate");
    }

    @Test
    void listaTodasAsEquipes() {
        service.criar(equipeA());
        service.criar(new TeamRequest("EQ-02", "Equipe B", Turno.TARDE, List.of("EST-02"), null));

        assertThat(service.listar()).extracting(TeamResponse::id)
                .containsExactlyInAnyOrder("EQ-01", "EQ-02");
    }
}
