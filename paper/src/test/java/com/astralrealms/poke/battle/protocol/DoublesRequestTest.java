package com.astralrealms.poke.battle.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DoublesRequestTest {

    static final ChoiceRequest MOVES = ChoiceRequest.parse("""
            {"active":[
              {"moves":[
                {"move":"Ember Tap","id":"embertap","pp":24,"maxpp":24,"target":"normal","disabled":false},
                {"move":"Heat Ring","id":"heatring","pp":16,"maxpp":16,"target":"allAdjacentFoes","disabled":false}]},
              {"moves":[
                {"move":"Leaf Cut","id":"leafcut","pp":12,"maxpp":16,"target":"any","disabled":false},
                {"move":"Mend Pulse","id":"mendpulse","pp":10,"maxpp":10,"target":"adjacentAlly","disabled":false},
                {"move":"Spore Puff","id":"sporepuff","pp":0,"maxpp":10,"target":"adjacentFoe","disabled":true}],
               "trapped":true}],
             "side":{"pokemon":[
              {"ident":"p1: 1","condition":"30/38","active":true},
              {"ident":"p1: 2","condition":"21/31","active":true},
              {"ident":"p1: 3","condition":"18/20","active":false},
              {"ident":"p1: 4","condition":"0 fnt","active":false},
              {"ident":"p1: 5","condition":"25/25 par","active":false}]},
             "rqid":7}""");

    @Test
    void readsOneSlotPerActiveCreature() {
        assertThat(MOVES.doubles()).isTrue();
        assertThat(MOVES.slots()).hasSize(2);
        assertThat(MOVES.forceSwitch()).isFalse();

        ChoiceRequest.Slot first = MOVES.slots().get(0);
        assertThat(first.index()).isZero();
        assertThat(first.idle()).isFalse();
        assertThat(first.trapped()).isFalse();
        assertThat(first.moves()).extracting(ChoiceRequest.MoveOption::target)
                .containsExactly("normal", "allAdjacentFoes");

        ChoiceRequest.Slot second = MOVES.slots().get(1);
        assertThat(second.trapped()).isTrue();
        assertThat(second.moves()).extracting(ChoiceRequest.MoveOption::id)
                .containsExactly("leafcut", "mendpulse", "sporepuff");
        assertThat(second.canUseMove(3)).isFalse();

        // The first slot stays what singles callers read
        assertThat(MOVES.moves()).isEqualTo(first.moves());
        assertThat(MOVES.trapped()).isFalse();
        // The PokeSMP choices (ball, bag, flee) take a whole side's turn: not in doubles
        assertThat(MOVES.canAct()).isFalse();
        // The second slot is trapped, not the first: each slot switches on its own
        assertThat(MOVES.onBench(3)).isTrue();
        assertThat(MOVES.onBench(2)).isFalse();
        assertThat(MOVES.onBench(4)).isFalse();
    }

    @Test
    void readsForcedSwitchesPerSlot() {
        ChoiceRequest request = ChoiceRequest.parse("""
                {"forceSwitch":[true,false],"side":{"pokemon":[
                  {"ident":"p1: 1","condition":"0 fnt","active":true},
                  {"ident":"p1: 2","condition":"21/31","active":true},
                  {"ident":"p1: 3","condition":"18/20","active":false}]},"rqid":8}""");
        assertThat(request.forceSwitch()).isTrue();
        assertThat(request.slots()).extracting(ChoiceRequest.Slot::forced).containsExactly(true, false);
        assertThat(request.slots()).extracting(ChoiceRequest.Slot::idle).containsExactly(false, true);
        assertThat(request.slots()).allMatch(slot -> slot.moves().isEmpty());
    }

    @Test
    void aFaintedSlotWithNobodyToReplaceItPasses() {
        ChoiceRequest request = ChoiceRequest.parse("""
                {"active":[
                  {"moves":[{"move":"Ember Tap","id":"embertap","pp":24,"maxpp":24,"target":"normal"}]},
                  {"moves":[{"move":"Leaf Cut","id":"leafcut","pp":12,"maxpp":16,"target":"any"}]}],
                 "side":{"pokemon":[
                  {"ident":"p1: 1","condition":"0 fnt","active":true},
                  {"ident":"p1: 2","condition":"21/31","active":true}]},"rqid":9}""");
        assertThat(request.slots()).extracting(ChoiceRequest.Slot::idle).containsExactly(true, false);
    }

    @Test
    void aSinglesRequestHasOneSlot() {
        ChoiceRequest request = ChoiceRequest.parse("""
                {"active":[{"moves":[{"id":"sparkjab","pp":3,"maxpp":24}]}],
                 "side":{"pokemon":[{"ident":"p1: 1","condition":"30/38","active":true}]},"rqid":2}""");
        assertThat(request.doubles()).isFalse();
        assertThat(request.slots()).hasSize(1);
        assertThat(request.slots().getFirst().idle()).isFalse();
        assertThat(request.moves().getFirst().target()).isEmpty();
    }

    @Test
    void targetsFollowShowdownLocations() {
        // Foes are positive (1 = p2a), the side's own negative (-1 = its a)
        assertThat(ChoiceRequest.targets("normal", 0, 2)).containsExactly(1, 2, -2);
        assertThat(ChoiceRequest.targets("any", 1, 2)).containsExactly(1, 2, -1);
        assertThat(ChoiceRequest.targets("adjacentFoe", 0, 2)).containsExactly(1, 2);
        assertThat(ChoiceRequest.targets("adjacentAlly", 0, 2)).containsExactly(-2);
        assertThat(ChoiceRequest.targets("adjacentAllyOrSelf", 1, 2)).containsExactly(-1, -2);
        assertThat(ChoiceRequest.targets("allAdjacentFoes", 0, 2)).isEmpty();
        assertThat(ChoiceRequest.targets("self", 0, 2)).isEmpty();
        // Singles never takes a target
        assertThat(ChoiceRequest.targets("normal", 0, 1)).isEmpty();
    }

    @Test
    void aCommandingCreatureAndAnEmptyPositionPass() {
        ChoiceRequest commanding = ChoiceRequest.parse("""
                {"active":[
                  {"moves":[{"move":"Ember Tap","id":"embertap","pp":24,"maxpp":24,"target":"normal"}]},
                  {"moves":[{"move":"Leaf Cut","id":"leafcut","pp":12,"maxpp":16,"target":"any"}]}],
                 "side":{"pokemon":[
                  {"ident":"p1: 1","condition":"30/38","active":true,"commanding":true},
                  {"ident":"p1: 2","condition":"21/31","active":true,"commanding":false}]},"rqid":11}""");
        assertThat(commanding.slots()).extracting(ChoiceRequest.Slot::idle).containsExactly(true, false);
        assertThat(SlotChoices.legal(commanding, "pass, move 1 1")).isTrue();
        assertThat(SlotChoices.legal(commanding, "move 1 1, move 1 2")).isFalse();

        // A side with a single creature on a doubles field: its second position comes as null
        ChoiceRequest alone = ChoiceRequest.parse("""
                {"active":[
                  {"moves":[{"move":"Ember Tap","id":"embertap","pp":24,"maxpp":24,"target":"normal"}]},
                  null],
                 "side":{"pokemon":[{"ident":"p1: 1","condition":"30/38","active":true}]},"rqid":12}""");
        assertThat(alone.slots()).extracting(ChoiceRequest.Slot::idle).containsExactly(false, true);
        assertThat(SlotChoices.legal(alone, "move 1 2, pass")).isTrue();
    }

    @Test
    void revivalBlessingCanBringBackAFaintedPartnerOnTheField() {
        ChoiceRequest reviving = ChoiceRequest.parse("""
                {"forceSwitch":[true,false],"side":{"pokemon":[
                  {"ident":"p1: 1","condition":"30/38","active":true,"reviving":true},
                  {"ident":"p1: 2","condition":"0 fnt","active":true},
                  {"ident":"p1: 3","condition":"18/20","active":false},
                  {"ident":"p1: 4","condition":"0 fnt","active":false}]},"rqid":13}""");
        assertThat(reviving.reviving()).isTrue();
        assertThat(reviving.onBench(2)).isTrue();
        assertThat(reviving.onBench(4)).isTrue();
        assertThat(reviving.onBench(1)).isFalse();
        assertThat(reviving.onBench(3)).isFalse();
        assertThat(SlotChoices.legal(reviving, "switch 2, pass")).isTrue();
        assertThat(SlotChoices.legal(reviving, "switch 3, pass")).isFalse();
    }
}
