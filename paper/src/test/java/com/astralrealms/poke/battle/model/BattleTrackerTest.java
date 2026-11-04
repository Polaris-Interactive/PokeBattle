package com.astralrealms.poke.battle.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.astralrealms.poke.battle.protocol.ProtocolLine;
import com.astralrealms.poke.core.mechanics.progression.ExperienceGain;
import com.astralrealms.poke.core.model.stat.StatSpread;

class BattleTrackerTest {

    private static BattleTracker feed(String... lines) {
        BattleTracker tracker = new BattleTracker("p1");
        for (String line : lines) {
            ProtocolLine parsed = ProtocolLine.parse(line);
            if (parsed != null)
                tracker.accept(parsed);
        }
        return tracker;
    }

    @Test
    void tracksTheFieldAndTheConditions() {
        BattleTracker tracker = feed(
                "|switch|p1a: 1|Emberling, L10, M|31/31",
                "|switch|p2a: 1|Pebblit, L7|100/100",
                "|turn|1",
                "|move|p1a: 1|Ember Tap|p2a: 1",
                "|-damage|p2a: 1|40/100",
                "|-status|p2a: 1|brn",
                "|turn|2");

        assertThat(tracker.active("p1")).isEqualTo("1");
        assertThat(tracker.active("p2")).isEqualTo("1");
        assertThat(tracker.turn()).isEqualTo(2);
        assertThat(tracker.condition("p2", "1").percent()).isEqualTo(40);
        assertThat(tracker.condition("p2", "1").status()).isEqualTo("brn");
        assertThat(tracker.condition("p1", "1").hp()).isEqualTo(31);
    }

    @Test
    void recordsACatchWithTheCreaturesThatFought() {
        BattleTracker tracker = feed(
                "|switch|p1a: 1|Emberling, L10, M|31/31",
                "|switch|p2a: 1|Pebblit, L7|100/100",
                "|polaris-ball|p2a: 1|duskorb|2|free",
                "|switch|p1a: 2|Leafkit, L12|38/38");
        assertThat(tracker.capture()).isNull();

        tracker.accept(ProtocolLine.parse("|polaris-ball|p2a: 1|duskorb|3|caught"));
        assertThat(tracker.capture()).isEqualTo(new BattleTracker.Capture("1", "duskorb", java.util.Set.of("1", "2")));
        assertThat(tracker.defeats()).isEmpty();
    }

    @Test
    void creditsEveryCreatureThatFoughtAndStillStands() {
        BattleTracker tracker = feed(
                "|switch|p1a: 1|Emberling, L10, M|31/31",
                "|switch|p2a: 1|Pebblit, L7|100/100",
                "|switch|p1a: 2|Leafkit, L12|38/38",
                "|faint|p2a: 1",
                "|switch|p2a: 2|Pebblit, L8|100/100",
                "|faint|p1a: 2",
                "|switch|p1a: 3|Mossling, L5|20/20",
                "|faint|p2a: 2");

        assertThat(tracker.defeats()).containsExactly(
                new BattleTracker.Defeat("1", java.util.Set.of("1", "2")),
                new BattleTracker.Defeat("2", java.util.Set.of("3")));

        Map<String, RewardCalculator.Ally> allies = new java.util.LinkedHashMap<>();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        allies.put("1", new RewardCalculator.Ally(first, 4, 10, true));
        allies.put("2", new RewardCalculator.Ally(second, 2, 12, false));
        allies.put("3", new RewardCalculator.Ally(third, 9, 5, true));
        Map<String, RewardCalculator.Foe> foes = Map.of(
                "1", new RewardCalculator.Foe(60, 7, new StatSpread(0, 0, 1, 0, 0, 0)),
                "2", new RewardCalculator.Foe(60, 8, new StatSpread(0, 0, 1, 0, 0, 0)));

        List<ExperienceGain> gains = RewardCalculator.gains(tracker.defeats(), allies, foes,
                new RewardCalculator.Rules(1.0, true, 1));

        // The fainted creature earns nothing; the others get the full share of what they fought
        assertThat(gains).extracting(ExperienceGain::pokemonId).containsExactly(first, third);
        assertThat(gains.get(0).expectedVersion()).isEqualTo(4);
        assertThat(gains.get(0).experience()).isPositive();
        assertThat(gains.get(0).evs().def()).isEqualTo(1);
        assertThat(gains.get(1).evs().def()).isEqualTo(1);
        assertThat(gains.get(1).friendship()).isEqualTo(1);
    }
}
