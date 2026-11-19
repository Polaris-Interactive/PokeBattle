package com.astralrealms.poke.battle.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.astralrealms.poke.battle.protocol.ProtocolLine;

class DoublesTrackerTest {

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
    void tracksEachPositionOnItsOwn() {
        BattleTracker tracker = feed(
                "|gametype|doubles",
                "|switch|p1a: 1|Emberling, L10|30/38",
                "|switch|p1b: 2|Leafkit, L12|31/31",
                "|switch|p2a: 1|Pebblit, L7|100/100",
                "|switch|p2b: 2|Pebblit, L8|100/100",
                "|turn|1",
                "|-damage|p2b: 2|40/100",
                "|-status|p1b: 2|par",
                "|drag|p1a: 3|Mossling, L5|20/20");

        assertThat(tracker.doubles()).isTrue();
        assertThat(tracker.slots()).isEqualTo(2);
        assertThat(tracker.active("p1", 0)).isEqualTo("3");
        assertThat(tracker.active("p1", 1)).isEqualTo("2");
        assertThat(tracker.active("p1")).isEqualTo("3");
        assertThat(tracker.active("p2", 0)).isEqualTo("1");
        assertThat(tracker.active("p2", 1)).isEqualTo("2");
        assertThat(tracker.condition("p2", "2").percent()).isEqualTo(40);
        assertThat(tracker.condition("p2", "1").percent()).isEqualTo(100);
        assertThat(tracker.condition("p1", "2").status()).isEqualTo("par");
    }

    @Test
    void seesDoublesFromTheSecondPositionWithoutAGameType() {
        BattleTracker tracker = feed(
                "|switch|p1a: 1|Emberling, L10|30/38",
                "|switch|p1b: 2|Leafkit, L12|31/31");
        assertThat(tracker.doubles()).isTrue();
        assertThat(feed("|switch|p1a: 1|Emberling, L10|30/38").doubles()).isFalse();
    }

    @Test
    void aFaintedCreatureStaysUntilReplacedAndEachOpponentCreditsWhoFoughtIt() {
        BattleTracker tracker = feed(
                "|gametype|doubles",
                "|switch|p1a: 1|Emberling, L10|30/38",
                "|switch|p1b: 2|Leafkit, L12|31/31",
                "|switch|p2a: 1|Pebblit, L7|100/100",
                "|switch|p2b: 2|Pebblit, L8|100/100",
                "|faint|p2a: 1",
                "|switch|p2a: 3|Pebblit, L9|100/100",
                "|faint|p1b: 2",
                "|faint|p2b: 2");

        assertThat(tracker.active("p1", 1)).isEqualTo("2");
        assertThat(tracker.condition("p1", "2").fainted()).isTrue();
        assertThat(tracker.active("p2", 0)).isEqualTo("3");
        assertThat(tracker.condition("p2", "2").fainted()).isTrue();
        // The fainted ally earns nothing from the opponents still out when it fell
        assertThat(tracker.defeats()).containsExactly(
                new BattleTracker.Defeat("1", Set.of("1", "2")),
                new BattleTracker.Defeat("2", Set.of("1")));
    }

    @Test
    void allySwitchTradesThePositionsOfASide() {
        BattleTracker tracker = feed(
                "|gametype|doubles",
                "|switch|p1a: 1|Emberling, L10|30/38",
                "|switch|p1b: 2|Leafkit, L12|31/31",
                "|switch|p2a: 1|Pebblit, L7|100/100",
                "|switch|p2b: 2|Mosslet, L8|100/100",
                "|swap|p2a: 1|1|[from] move: Ally Switch");

        assertThat(tracker.active("p2", 0)).isEqualTo("2");
        assertThat(tracker.active("p2", 1)).isEqualTo("1");
        assertThat(tracker.active("p1", 0)).isEqualTo("1");

        // Back again, from the creature's new position
        tracker.accept(ProtocolLine.parse("|swap|p2b: 1|0|[from] move: Ally Switch"));
        assertThat(tracker.active("p2", 0)).isEqualTo("1");
        assertThat(tracker.active("p2", 1)).isEqualTo("2");
    }
}
