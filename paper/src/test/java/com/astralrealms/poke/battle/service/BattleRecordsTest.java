package com.astralrealms.poke.battle.service;

import static org.assertj.core.api.Assertions.assertThat;

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
}
