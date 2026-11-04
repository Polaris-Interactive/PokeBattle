package com.astralrealms.poke.battle.api.service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.astralrealms.poke.battle.api.model.BattleRequest;
import com.astralrealms.poke.battle.api.model.BattleStartResult;

/**
 * Battles shown by this server. The simulation runs on a battle worker; this server locks the players, shows the
 * battle, takes their choices and settles the outcome through PokeCore.
 */
public interface BattleService {

    /**
     * Starts a battle; completes once a worker took it (or refused it, or none answered).
     */
    CompletableFuture<BattleStartResult> start(BattleRequest request);

    /**
     * The battle a player is in on this server, if any.
     */
    Optional<UUID> battleOf(UUID playerId);

    default boolean inBattle(UUID playerId) {
        return this.battleOf(playerId).isPresent();
    }

    /**
     * Stops a battle with no winner and no rewards; the creatures keep the condition it left them in.
     *
     * @return false if the battle is not shown here
     */
    boolean abort(UUID battleId, String reason);
}
