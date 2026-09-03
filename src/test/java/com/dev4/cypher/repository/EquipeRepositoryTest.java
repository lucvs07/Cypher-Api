package com.dev4.cypher.repository;

import com.dev4.cypher.model.Equipe;
import com.dev4.cypher.model.Turno;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class EquipeRepositoryTest {

    @Autowired
    private EquipeRepository equipes;

    @Test
    void persisteEstacaoIdsComoElementCollection() {
        equipes.saveAndFlush(new Equipe("demo:EQ-01", "Equipe A", Turno.MANHA,
                List.of("demo:EST-01", "demo:EST-02"), true, "demo"));

        Equipe lida = equipes.findById("demo:EQ-01").orElseThrow();
        assertThat(lida.getEstacaoIds()).containsExactlyInAnyOrder("demo:EST-01", "demo:EST-02");
        assertThat(lida.getTurno()).isEqualTo(Turno.MANHA);
    }

    @Test
    void listaApenasEquipesAtivas() {
        equipes.save(new Equipe("demo:EQ-01", "Ativa", Turno.MANHA, List.of("demo:EST-01"), true, "demo"));
        equipes.saveAndFlush(new Equipe("demo:EQ-02", "Inativa", Turno.TARDE, List.of("demo:EST-02"), false, "demo"));

        assertThat(equipes.findByAtivaTrue()).extracting(Equipe::getId).containsExactly("demo:EQ-01");
    }

    @Test
    void encontraEquipesPeloTurno() {
        equipes.save(new Equipe("demo:EQ-01", "Manha", Turno.MANHA, List.of("demo:EST-01"), true, "demo"));
        equipes.save(new Equipe("demo:EQ-02", "Tarde", Turno.TARDE, List.of("demo:EST-01"), true, "demo"));
        equipes.saveAndFlush(new Equipe("demo:EQ-03", "Manha 2", Turno.MANHA, List.of("demo:EST-02"), true, "demo"));

        assertThat(equipes.findByTurno(Turno.MANHA)).extracting(Equipe::getId)
                .containsExactlyInAnyOrder("demo:EQ-01", "demo:EQ-03");
    }
}
