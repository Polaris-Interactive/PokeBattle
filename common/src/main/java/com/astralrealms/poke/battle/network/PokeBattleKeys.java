package com.astralrealms.poke.battle.network;

import java.util.UUID;

import lombok.experimental.UtilityClass;

/**
 * Redis keys shared by the shards and the workers.
 * <ul>
 *     <li>{@link #battle}: hash {@code workerId, instance, shardId, format, seed, turn, createdAt, start}. Claimed by a
 *     worker with {@code HSETNX workerId} before it accepts a start, so a redelivered start is never run twice; taken
 *     over by another worker when {@code instance} no longer beats. TTL refreshed on every turn. Once ended:
 *     {@code result, endedAt, ended} (the ended packet, base64) and {@code settledBy}, the shard that settled it.</li>
 *     <li>{@link #battleContext}: what a shard needs to show and settle the battle besides the worker's packets
 *     (written by the shard that started it, JSON), so another shard can take it over.</li>
 *     <li>{@link #battleLog}: list of the simulator inputs in order, enough to replay the battle on another worker
 *     (the simulator is deterministic from its seed).</li>
 *     <li>{@link #worker}: hash {@code instance, battles, capacity, startedAt}, a heartbeat with a TTL of a few
 *     seconds. A missing key, or another {@code instance} than the battle's, means its battles are lost with it.</li>
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

    public static String battleContext(UUID battleId) {
        return PREFIX + "battle:" + battleId + ":context";
    }

    public static String worker(String workerId) {
        return PREFIX + "worker:" + workerId;
    }

    public static String playerBattle(UUID playerId) {
        return PREFIX + "player:" + playerId + ":battle";
    }
}
