package com.astralrealms.poke.battle.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class BattleRecordsTest {

    @Test
    void settlementClaimRepliesReadAsClaims() {
        assertThat(BattleRecords.claim(1L)).isEqualTo(BattleRecords.Claim.CLAIMED);
        assertThat(BattleRecords.claim(2L)).isEqualTo(BattleRecords.Claim.RETAKEN);
        assertThat(BattleRecords.claim(0L)).isEqualTo(BattleRecords.Claim.SETTLED);
        assertThat(BattleRecords.claim(-1L)).isEqualTo(BattleRecords.Claim.BUSY);
        // No reply settles nothing here: asked again later
        assertThat(BattleRecords.claim(null)).isEqualTo(BattleRecords.Claim.BUSY);
    }

    @Test
    void batchedStatesReadInTheOrderAsked() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        var states = BattleRecords.states(List.of(first, second),
                List.of("ALIVE", "w1", "shard-a", "MISSING", "", ""));

        assertThat(states.get(first)).isEqualTo(new BattleRecords.State(BattleRecords.Status.ALIVE, "w1", "shard-a"));
        assertThat(states.get(second)).isEqualTo(new BattleRecords.State(BattleRecords.Status.MISSING, null, null));
    }
}
