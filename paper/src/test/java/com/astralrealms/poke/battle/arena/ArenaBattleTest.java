package com.astralrealms.poke.battle.arena;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ArenaBattleTest {

    @Test
    void travelsInTheInstanceMetadataAsJson() {
        ArenaBattle battle = new ArenaBattle("polariswild", "fakemon", null, 12, Set.of("night"));

        assertThat(ArenaBattle.fromJson(battle.toJson())).isEqualTo(battle);
    }

    @Test
    void missingFlagsAreEmpty() {
        assertThat(ArenaBattle.fromJson("{\"format\":\"polariswild\",\"species\":\"fakemon\",\"level\":5}")
                .captureFlags()).isEmpty();
    }
}
