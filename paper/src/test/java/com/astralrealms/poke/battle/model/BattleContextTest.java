package com.astralrealms.poke.battle.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.astralrealms.poke.core.mechanics.generation.GeneratedCreature;
import com.astralrealms.poke.core.mechanics.generation.SignedRoll;
import com.astralrealms.poke.core.model.Gender;
import com.astralrealms.poke.core.model.stat.StatSpread;

class BattleContextTest {

    @Test
    void survivesRedisAsJson() {
        GeneratedCreature creature = new GeneratedCreature("fakemon", null, 7, new StatSpread(1, 2, 3, 4, 5, 6),
                "calm", "ability-a", true, Gender.FEMALE, false, List.of("move-a", "move-b"), 70);
        SignedRoll roll = new SignedRoll(UUID.randomUUID(), 1234L, creature, "c2lnbmF0dXJl");
        BattleContext context = new BattleContext(UUID.randomUUID(), "polariswild", UUID.randomUUID(),
                List.of(UUID.randomUUID(), UUID.randomUUID()), List.of(creature),
                new Battle.Options(true, true, false, roll, Set.of("night", "water"), "world"));

        BattleContext read = BattleContext.fromJson(context.toJson());

        assertThat(read).isEqualTo(context);
        assertThat(read.options().wildRoll()).isEqualTo(roll);
    }
}
