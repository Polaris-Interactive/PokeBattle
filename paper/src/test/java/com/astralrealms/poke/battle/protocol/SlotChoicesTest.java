package com.astralrealms.poke.battle.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;


class SlotChoicesTest {

    private static final ChoiceRequest MOVES = DoublesRequestTest.MOVES;

    @Test
    void oneChoicePerSlotWithTargetsWhereNeeded() {
        assertThat(SlotChoices.legal(MOVES, "move 1 2, move 1 -1")).isTrue();
        assertThat(SlotChoices.legal(MOVES, "move 2, move 2 -1")).isTrue();
        assertThat(SlotChoices.legal(MOVES, "switch 3, move 1 1")).isTrue();

        // A targeted move needs a target, an untargeted one takes none, and only reachable ones
        assertThat(SlotChoices.legal(MOVES, "move 1, move 1 1")).isFalse();
        assertThat(SlotChoices.legal(MOVES, "move 2 1, move 1 1")).isFalse();
        assertThat(SlotChoices.legal(MOVES, "move 1 3, move 1 1")).isFalse();
        assertThat(SlotChoices.legal(MOVES, "move 1 -1, move 1 1")).isFalse();
        assertThat(SlotChoices.legal(MOVES, "move 1 1, move 2 1")).isFalse();
        // Out of PP
        assertThat(SlotChoices.legal(MOVES, "move 1 1, move 3 1")).isFalse();
        // Every slot answers, none passes with something to do
        assertThat(SlotChoices.legal(MOVES, "move 1 1")).isFalse();
        assertThat(SlotChoices.legal(MOVES, "pass, move 1 1")).isFalse();
        assertThat(SlotChoices.legal(MOVES, "move 1 1, move 1 1, move 1 1")).isFalse();
        assertThat(SlotChoices.legal(MOVES, "run")).isFalse();
        assertThat(SlotChoices.legal(MOVES, "ball pokeball 3 1, move 1 1")).isFalse();
    }

    @Test
    void neverTheSameBenchMemberTwiceAndNoSwitchWhenTrapped() {
        assertThat(SlotChoices.legal(MOVES, "switch 3, switch 3")).isFalse();
        // The second slot is trapped
        assertThat(SlotChoices.legal(MOVES, "switch 3, switch 5")).isFalse();
        assertThat(SlotChoices.legal(MOVES, "switch 4, move 1 1")).isFalse();
        assertThat(SlotChoices.legal(MOVES, "switch 2, move 1 1")).isFalse();
    }

    @Test
    void forcedSwitchesPassOnlyWithNobodyLeft() {
        ChoiceRequest one = ChoiceRequest.parse("""
                {"forceSwitch":[true,false],"side":{"pokemon":[
                  {"ident":"p1: 1","condition":"0 fnt","active":true},
                  {"ident":"p1: 2","condition":"21/31","active":true},
                  {"ident":"p1: 3","condition":"18/20","active":false}]},"rqid":8}""");
        assertThat(SlotChoices.legal(one, "switch 3, pass")).isTrue();
        assertThat(SlotChoices.legal(one, "pass, pass")).isFalse();
        assertThat(SlotChoices.legal(one, "pass, switch 3")).isFalse();
        assertThat(SlotChoices.legal(one, "switch 3, switch 3")).isFalse();

        ChoiceRequest both = ChoiceRequest.parse("""
                {"forceSwitch":[true,true],"side":{"pokemon":[
                  {"ident":"p1: 1","condition":"0 fnt","active":true},
                  {"ident":"p1: 2","condition":"0 fnt","active":true},
                  {"ident":"p1: 3","condition":"18/20","active":false}]},"rqid":10}""");
        assertThat(SlotChoices.legal(both, "switch 3, pass")).isTrue();
        assertThat(SlotChoices.legal(both, "pass, switch 3")).isTrue();
        assertThat(SlotChoices.legal(both, "pass, pass")).isFalse();
        assertThat(SlotChoices.legal(both, "switch 3, switch 3")).isFalse();
    }

    @Test
    void aFaintedSlotPasses() {
        ChoiceRequest request = ChoiceRequest.parse("""
                {"active":[
                  {"moves":[{"move":"Ember Tap","id":"embertap","pp":24,"maxpp":24,"target":"normal"}]},
                  {"moves":[{"move":"Leaf Cut","id":"leafcut","pp":12,"maxpp":16,"target":"any"}]}],
                 "side":{"pokemon":[
                  {"ident":"p1: 1","condition":"0 fnt","active":true},
                  {"ident":"p1: 2","condition":"21/31","active":true}]},"rqid":9}""");
        assertThat(SlotChoices.legal(request, "pass, move 1 1")).isTrue();
        assertThat(SlotChoices.legal(request, "move 1 1, move 1 1")).isFalse();
    }

    @Test
    void joinsAndSplitsAnswers() {
        assertThat(SlotChoices.join(List.of("move 1 2", "switch 3"))).isEqualTo("move 1 2, switch 3");
        assertThat(SlotChoices.split("move 1 2,switch 3")).containsExactly("move 1 2", "switch 3");
        assertThat(SlotChoices.switched("switch 3")).isEqualTo(3);
        assertThat(SlotChoices.switched("move 1 2")).isEqualTo(-1);
    }

    @Test
    void eachPickIsCheckedAgainstItsOwnSlot() {
        assertThat(SlotChoices.legal(MOVES, 0, "move 1 2", List.of())).isTrue();
        // Out of PP, or a targeted move without its target: refused before the next slot is asked
        assertThat(SlotChoices.legal(MOVES, 1, "move 3 1", List.of("move 1 2"))).isFalse();
        assertThat(SlotChoices.legal(MOVES, 0, "move 1", List.of())).isFalse();
        // A bench member already picked, a trapped slot
        assertThat(SlotChoices.legal(MOVES, 0, "switch 3", List.of())).isTrue();
        assertThat(SlotChoices.legal(MOVES, 1, "switch 3", List.of("switch 3"))).isFalse();
        assertThat(SlotChoices.legal(MOVES, 1, "switch 5", List.of("move 1 2"))).isFalse();
        // The picks before must be one per slot
        assertThat(SlotChoices.legal(MOVES, 1, "move 1 1", List.of())).isFalse();
    }
}
