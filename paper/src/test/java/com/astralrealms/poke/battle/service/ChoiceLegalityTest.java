package com.astralrealms.poke.battle.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.astralrealms.poke.battle.protocol.ChoiceRequest;

class ChoiceLegalityTest {

    private static final ChoiceRequest REQUEST = ChoiceRequest.parse("""
            {"active":[{"moves":[
              {"id":"sparkjab","pp":0,"maxpp":24,"disabled":true},
              {"id":"leafcut","pp":12,"maxpp":16,"disabled":false}]}],
             "side":{"pokemon":[
              {"ident":"p1: 1","condition":"30/38","active":true},
              {"ident":"p1: 2","condition":"21/31 brn","active":false},
              {"ident":"p1: 3","condition":"0 fnt","active":false}]},
             "rqid":2}""");

    @Test
    void onlyWellFormedLegalChoicesLeave() {
        assertThat(BattleServiceImpl.legal(REQUEST, "move 2")).isTrue();
        assertThat(BattleServiceImpl.legal(REQUEST, "switch 2")).isTrue();

        assertThat(BattleServiceImpl.legal(REQUEST, "move 1")).isFalse();
        assertThat(BattleServiceImpl.legal(REQUEST, "move 5")).isFalse();
        assertThat(BattleServiceImpl.legal(REQUEST, "switch 1")).isFalse();
        assertThat(BattleServiceImpl.legal(REQUEST, "switch 3")).isFalse();
        assertThat(BattleServiceImpl.legal(REQUEST, "move two")).isFalse();
        assertThat(BattleServiceImpl.legal(REQUEST, "move 2 mega")).isFalse();
        assertThat(BattleServiceImpl.legal(REQUEST, "forcewin p1")).isFalse();
        // Balls and bag items are built by the shard, never typed
        assertThat(BattleServiceImpl.legal(REQUEST, "ball pokeball 3 1")).isFalse();
        assertThat(BattleServiceImpl.legal(REQUEST, "item potion 1 heal=20")).isFalse();
    }

    @Test
    void fleeingNeedsAMoveRequest() {
        assertThat(BattleServiceImpl.legal(REQUEST, "run")).isTrue();
        ChoiceRequest forced = ChoiceRequest.parse("""
                {"forceSwitch":[true],"side":{"pokemon":[
                  {"ident":"p1: 1","condition":"0 fnt","active":true},
                  {"ident":"p1: 2","condition":"21/31","active":false}]},"rqid":3}""");
        assertThat(BattleServiceImpl.legal(forced, "run")).isFalse();
        assertThat(BattleServiceImpl.legal(forced, "switch 2")).isTrue();
    }

    @Test
    void revivalBlessingSwitchesToAFaintedMember() {
        ChoiceRequest reviving = ChoiceRequest.parse("""
                {"forceSwitch":[true],"side":{"pokemon":[
                  {"ident":"p1: 1","condition":"30/38","active":true,"reviving":true},
                  {"ident":"p1: 2","condition":"21/31","active":false},
                  {"ident":"p1: 3","condition":"0 fnt","active":false}]},"rqid":4}""");
        assertThat(reviving.reviving()).isTrue();
        assertThat(BattleServiceImpl.legal(reviving, "switch 3")).isTrue();
        assertThat(BattleServiceImpl.legal(reviving, "switch 2")).isFalse();
    }
}
