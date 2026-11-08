package com.astralrealms.poke.battle.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.astralrealms.poke.battle.api.model.BattleRequest;
import com.astralrealms.poke.battle.api.model.BattleRules;
import com.astralrealms.poke.battle.api.model.TrainerOpponent;
import com.astralrealms.poke.core.mechanics.generation.GeneratedCreature;
import com.astralrealms.poke.core.mechanics.generation.SignedRoll;
import com.astralrealms.poke.core.model.Gender;
import com.astralrealms.poke.core.model.stat.StatSpread;
import com.astralrealms.poke.core.team.TrainerMember;

class BattleRequestTest {

    private static final TrainerOpponent TRAINER = new TrainerOpponent("Pierre",
            List.of(TrainerMember.of("fakemon", null, 12)), "gym");

    @Test
    void wildRequestKeepsItsDefaults() {
        GeneratedCreature creature = new GeneratedCreature("fakemon", null, 7, StatSpread.of(31), "calm", "ability-a",
                false, Gender.MALE, false, List.of("move-a"), 70);
        BattleRequest request = BattleRequest.wild(null, new SignedRoll(UUID.randomUUID(), 1L, creature, "sig"),
                Set.of("night"));
        assertThat(request.format()).isEqualTo(BattleRequest.WILD_FORMAT);
        assertThat(request.rules()).isEqualTo(BattleRules.WILD);
        assertThat(request.trainer()).isNull();
        assertThat(request.tag()).isNull();
    }

    @Test
    void trainerRequestCarriesItsTagThroughStaging() {
        BattleRequest request = BattleRequest.trainer(null, TRAINER, BattleRequest.GYM_FORMAT, BattleRules.GYM,
                "poketrainers:pierre");
        assertThat(request.wild()).isNull();
        assertThat(request.rules().bag()).isFalse();
        assertThat(request.tag()).isEqualTo("poketrainers:pierre");
        assertThat(request.withTag("other").tag()).isEqualTo("other");
    }

    @Test
    void exactlyOneOpponent() {
        assertThatThrownBy(() -> new BattleRequest("polaristrainer", null, null, null, Set.of(), BattleRules.TRAINER,
                null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trainerTeamIsOneToSix() {
        assertThatThrownBy(() -> new TrainerOpponent("Pierre", List.of(), "trainer"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrainerOpponent("Pierre",
                Collections.nCopies(7, TrainerMember.of("fakemon", null, 5)), "trainer"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rulesRefuseANegativeMultiplier() {
        assertThatThrownBy(() -> new BattleRules(true, false, false, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(BattleRules.TRAINER.experienceMultiplier()).isEqualTo(1.5);
    }
}
