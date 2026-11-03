package com.astralrealms.poke.battle.network;

import lombok.experimental.UtilityClass;

/**
 * RabbitMQ names of the battle protocol. RabbitMQ is the router: starts go through one shared queue that workers
 * pull from when they have room; a battle's later messages go to the worker that took it and to the shard showing it.
 * <ul>
 *     <li>{@link #START}: exchange (fanout, durable) bound to the durable queue of the same name. Declared by the
 *     workers, consumed by all of them in competition. Shards publish {@code BattleStartPacket} with a reply.</li>
 *     <li>{@link #worker}: one fanout exchange per worker, declared by it (durable, not auto-deleted, so a shard
 *     publishing to a dead worker gets its message returned instead of losing its channel). Shard to worker.</li>
 *     <li>{@link #shard}: one fanout exchange per Paper server, registered by PokeBattle through AstralCore. Worker
 *     to shard.</li>
 * </ul>
 */
@UtilityClass
public class PokeBattleExchanges {

    public static final String START = "pokebattle.start";

    public static String worker(String workerId) {
        return "pokebattle.worker." + workerId;
    }

    public static String shard(String shardId) {
        return "pokebattle.shard." + shardId;
    }
}
