package com.astralrealms.poke.battle.network;

import java.util.UUID;

import lombok.experimental.UtilityClass;

/**
 * Redis keys shared by the shards and the workers.
 * <ul>
 *     <li>{@link #battle}: hash {@code workerId, shardId, format, seed, turn, createdAt}. Claimed by a worker with
 *     {@code HSETNX workerId} before it accepts a start, so a redelivered start is never run twice. TTL refreshed
 *     on every turn.</li>
 *     <li>{@link #battleLog}: list of the simulator inputs in order, enough to replay the battle on another worker
 *     (the simulator is deterministic from its seed).</li>
 *     <li>{@link #worker}: hash {@code battles, capacity, startedAt}, a heartbeat with a TTL of a few seconds. A
 *     missing key means the worker is dead.</li>
 *     <li>{@link #playerBattle}: the battle a player is in, the per-player lock.</li>
 * </ul>
 */
@UtilityClass
public class PokeBattleKeys {

    private static final String PREFIX = "pokebattle:";

    public static String battle(UUID battleId) {
        return PREFIX + "battle:" + battleId;
    }

    public static String battleLog(UUID battleId) {
        return PREFIX + "battle:" + battleId + ":log";
    }

    public static String worker(String workerId) {
        return PREFIX + "worker:" + workerId;
    }

    public static String playerBattle(UUID playerId) {
        return PREFIX + "player:" + playerId + ":battle";
    }
}
