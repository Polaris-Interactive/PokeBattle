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
        assertThat(cue("|-damage|p2a: 1|40/100")).isEqualTo(BattleCue.FOE_DAMAGE);
        assertThat(cue("|-damage|p1a: 1|12/30 psn|[from] psn")).isEqualTo(BattleCue.ALLY_DAMAGE);
        assertThat(cue("|-boost|p1a: 1|atk|2")).isEqualTo(BattleCue.ALLY_BOOST);
        assertThat(cue("|-unboost|p2a: 1|accuracy|1")).isEqualTo(BattleCue.FOE_UNBOOST);
    }

    @Test
    void captureAndEndHaveTheirCues() {
        assertThat(cue("|polaris-ball|p1|testball|p2a: 1|caught")).isEqualTo(BattleCue.CAPTURE);
        assertThat(cue("|win|Someone")).isEqualTo(BattleCue.END);
        assertThat(cue("|tie")).isEqualTo(BattleCue.END);
    }

    @Test
    void theRestShowsNothing() {
        assertThat(cue("|turn|2")).isNull();
        assertThat(cue("|move|nobody|testmove")).isNull();
        assertThat(cue("|-unboost|p2a: 1|atk|0")).isNull();
    }

    @Test
    void moveLinesNameTheirMoveWhenShown() {
        assertThat(BattleCue.move(ProtocolLine.parse("|move|p1a: 1|Test Move|p2a: 1"))).isEqualTo("testmove");
        assertThat(BattleCue.move(ProtocolLine.parse("|move|p2a: 1|Test Move|p1a: 1|[miss]"))).isEqualTo("testmove");
        assertThat(BattleCue.move(ProtocolLine.parse("|move|p1a: 1|Test Move||[still]"))).isNull();
        assertThat(BattleCue.move(ProtocolLine.parse("|move|p1a: 1|Test Move|p2a: 1|[notarget]"))).isNull();
        assertThat(BattleCue.move(ProtocolLine.parse("|-damage|p2a: 1|40/100"))).isNull();
    }

    @Test
    void keysAreKebabCase() {
        assertThat(BattleCue.ALLY_MOVE.key()).isEqualTo("ally-move");
        assertThat(BattleCue.END.key()).isEqualTo("end");
    }
}
