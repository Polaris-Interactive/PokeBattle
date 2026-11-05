package com.astralrealms.poke.battle.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.astralrealms.poke.battle.protocol.ProtocolLine;

class BattleCueTest {

    private static BattleCue cue(String line) {
        return BattleCue.of(ProtocolLine.parse(line));
    }

    @Test
    void sidesAreSeenFromThePlayer() {
        assertThat(cue("|move|p1a: 1|testmove|p2a: 1")).isEqualTo(BattleCue.ALLY_MOVE);
        assertThat(cue("|move|p2a: 1|testmove|p1a: 1")).isEqualTo(BattleCue.FOE_MOVE);
        assertThat(cue("|switch|p1a: 2|Fixturemon, L12|30/30")).isEqualTo(BattleCue.ALLY_SWITCH);
        assertThat(cue("|drag|p2a: 1|Fixturemon, L12|100/100")).isEqualTo(BattleCue.FOE_SWITCH);
        assertThat(cue("|faint|p2a: 1")).isEqualTo(BattleCue.FOE_FAINT);
        assertThat(cue("|faint|p1a: 1")).isEqualTo(BattleCue.ALLY_FAINT);
    }

    @Test
    void captureAndEndHaveTheirCues() {
        assertThat(cue("|polaris-ball|p1|testball|p2a: 1|caught")).isEqualTo(BattleCue.CAPTURE);
        assertThat(cue("|win|Someone")).isEqualTo(BattleCue.END);
        assertThat(cue("|tie")).isEqualTo(BattleCue.END);
    }

    @Test
    void theRestShowsNothing() {
        assertThat(cue("|-damage|p2a: 1|50/100")).isNull();
        assertThat(cue("|turn|2")).isNull();
        assertThat(cue("|move|nobody|testmove")).isNull();
    }

    @Test
    void keysAreKebabCase() {
        assertThat(BattleCue.ALLY_MOVE.key()).isEqualTo("ally-move");
        assertThat(BattleCue.END.key()).isEqualTo("end");
    }
}
