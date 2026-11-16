package com.astralrealms.poke.battle.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.astralrealms.poke.core.model.Pokemon;
import com.astralrealms.poke.core.model.storage.StorageLimits;
import com.astralrealms.poke.core.model.storage.TrainerStorage;

class CaptureRoomTest {

    private static final StorageLimits LIMITS = new StorageLimits(2, 2, 1);

    /** Only the slots matter here: who sits in them does not. */
    private static Map<Integer, Pokemon> taken(int... slots) {
        Map<Integer, Pokemon> map = new HashMap<>();
        for (int slot : slots)
            map.put(slot, null);
        return map;
    }

    @Test
    void aFreePartyOrPcSlotTakesTheCatch() {
        UUID owner = UUID.randomUUID();
        assertThat(BattleServiceImpl.hasRoom(new TrainerStorage(owner, taken(0), Map.of()), LIMITS)).isTrue();
        assertThat(BattleServiceImpl.hasRoom(new TrainerStorage(owner, taken(0, 1), Map.of(0, taken(1))), LIMITS))
                .isTrue();
    }

    @Test
    void aFullPartyAndPcRefuseTheThrow() {
        UUID owner = UUID.randomUUID();
        assertThat(BattleServiceImpl.hasRoom(new TrainerStorage(owner, taken(0, 1), Map.of(0, taken(0, 1))), LIMITS))
                .isFalse();
        // One more box (a rank perk) makes room
        assertThat(BattleServiceImpl.hasRoom(new TrainerStorage(owner, taken(0, 1), Map.of(0, taken(0, 1))),
                new StorageLimits(2, 2, 2))).isTrue();
    }
}
