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
        equipes.saveAndFlush(new Equipe("EQ-01", "Equipe A", Turno.MANHA,
                List.of("EST-01", "EST-02"), true));

        Equipe lida = equipes.findById("EQ-01").orElseThrow();
        assertThat(lida.getEstacaoIds()).containsExactlyInAnyOrder("EST-01", "EST-02");
        assertThat(lida.getTurno()).isEqualTo(Turno.MANHA);
    }

    @Test
    void listaApenasEquipesAtivas() {
        equipes.save(new Equipe("EQ-01", "Ativa", Turno.MANHA, List.of("EST-01"), true));
        equipes.saveAndFlush(new Equipe("EQ-02", "Inativa", Turno.TARDE, List.of("EST-02"), false));

        assertThat(equipes.findByAtivaTrue()).extracting(Equipe::getId).containsExactly("EQ-01");
    }

    @Test
    void encontraEquipesPeloTurno() {
        equipes.save(new Equipe("EQ-01", "Manha", Turno.MANHA, List.of("EST-01"), true));
        equipes.save(new Equipe("EQ-02", "Tarde", Turno.TARDE, List.of("EST-01"), true));
        equipes.saveAndFlush(new Equipe("EQ-03", "Manha 2", Turno.MANHA, List.of("EST-02"), true));

        assertThat(equipes.findByTurno(Turno.MANHA)).extracting(Equipe::getId)
                .containsExactlyInAnyOrder("EQ-01", "EQ-03");
    }
}
