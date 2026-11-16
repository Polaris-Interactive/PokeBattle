package com.astralrealms.poke.battle.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class BattleSpentTest {

    private static Battle battle() {
        return new Battle(UUID.randomUUID(), "polariswild", UUID.randomUUID(), List.of(), List.of(),
                new Battle.Options(true, true, true, null, Set.of(), "world"));
    }

    @Test
    void aChoiceRefusedAfterTheNextRequestStillGivesItsItemBack() {
        Battle battle = battle();
        battle.spend(new Battle.Spent(5, "items:example/standard-ball"));
        // The turn timer answered request 5: request 6 comes, then the late ball is refused as stale
        battle.requested(6);

        assertThat(battle.unspend(5)).isEqualTo(new Battle.Spent(5, "items:example/standard-ball"));
        assertThat(battle.unspend(5)).isNull();
    }

    @Test
    void anItemOfAChoicePlayedLongAgoIsNoLongerHeld() {
        Battle battle = battle();
        battle.spend(new Battle.Spent(5, "items:example/potion"));
        battle.requested(6);
        battle.requested(7);

        assertThat(battle.unspend(5)).isNull();
    }
}
