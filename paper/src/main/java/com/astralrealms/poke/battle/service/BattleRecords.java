package com.astralrealms.poke.battle.service;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.Nullable;

import com.astralrealms.core.cache.CacheService;
import com.astralrealms.core.packet.Packet;
import com.astralrealms.core.packet.io.PacketDeserializer;
import com.astralrealms.poke.battle.model.BattleContext;
import com.astralrealms.poke.battle.network.PokeBattleKeys;
import com.astralrealms.poke.battle.network.PokeBattlePacketRegistry;
import com.astralrealms.poke.battle.network.packet.BattleEndedPacket;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;

/**
 * A battle's records in Redis, as the shards see them: whether its worker still runs it, the end it left, who settled
 * it, and the context a shard needs to take it over (see {@link PokeBattleKeys}).
 */
public class BattleRecords {

    /**
     * Where a battle stands, network-wide.
     */
    public enum Status {
        /** Its worker beats. */
        ALIVE,
        /** Its worker is gone (no heartbeat, or a restarted process): it must be recovered on another one. */
        DEAD,
        /** It ended; its ended packet waits to be settled. */
        ENDED,
        /** No record (expired, or never started). */
        MISSING,
    }

    /**
     * @param workerId the worker that runs it, null when missing
     * @param shardId  the shard it reports to, null when missing
     */
    public record State(Status status, @Nullable String workerId, @Nullable String shardId) {
    }

    // Returns {status, workerId, shardId}
    private static final String STATE = """
            local owner = redis.call('HGET', KEYS[1], 'workerId')
            if not owner then
              return {'MISSING', '', ''}
            end
            local shard = redis.call('HGET', KEYS[1], 'shardId') or ''
            if redis.call('HEXISTS', KEYS[1], 'ended') == 1 then
              return {'ENDED', owner, shard}
            end
            local instance = redis.call('HGET', KEYS[1], 'instance')
            local beating = redis.call('HGET', ARGV[1] .. owner, 'instance')
            if beating and beating == instance then
              return {'ALIVE', owner, shard}
            end
            return {'DEAD', owner, shard}""";
    // Only one shard settles a battle; a battle whose record expired has nobody else to race with
    private static final String CLAIM_SETTLEMENT = """
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return 1
            end
            return redis.call('HSETNX', KEYS[1], 'settledBy', ARGV[1])""";

    private final CacheService cache;
    private final PacketDeserializer deserializer = new PacketDeserializer(new PokeBattlePacketRegistry());

    public BattleRecords(CacheService cache) {
        this.cache = cache;
    }

    public CompletableFuture<State> state(UUID battleId) {
        return this.cache.runAsync(commands -> commands
                .<List<String>>eval(STATE, ScriptOutputType.MULTI,
                        new String[]{PokeBattleKeys.battle(battleId)}, PokeBattleKeys.worker(""))
                .toCompletableFuture()
                .thenApply(result -> new State(Status.valueOf(result.get(0)),
                        result.get(1).isEmpty() ? null : result.get(1),
                        result.get(2).isEmpty() ? null : result.get(2))));
    }

    /**
     * @return true if this shard is the one that settles the battle
     */
    public CompletableFuture<Boolean> claimSettlement(UUID battleId, String shardId) {
        return this.cache.runAsync(commands -> commands
                .<Long>eval(CLAIM_SETTLEMENT, ScriptOutputType.INTEGER, new String[]{PokeBattleKeys.battle(battleId)},
                        shardId)
                .toCompletableFuture()
                .thenApply(result -> result != null && result == 1L));
    }

    /**
     * The end the worker left for a shard that missed it.
     */
    public CompletableFuture<Optional<BattleEndedPacket>> ended(UUID battleId) {
        return this.cache.runAsync(commands -> commands
                .hget(PokeBattleKeys.battle(battleId), "ended")
                .toCompletableFuture()
                .thenApply(encoded -> {
                    if (encoded == null)
                        return Optional.empty();
                    Packet packet = this.deserializer.deserialize(Base64.getDecoder().decode(encoded));
                    return packet instanceof BattleEndedPacket ended ? Optional.of(ended) : Optional.empty();
                }));
    }

    /**
     * Records that this shard now shows the battle (the worker does too once it gets the attach): until then a check
     * would see the previous shard and let the battle go.
     */
    public CompletableFuture<Void> moveTo(UUID battleId, String shardId) {
        return this.cache.runAsync(commands -> commands
                .hset(PokeBattleKeys.battle(battleId), "shardId", shardId)
                .toCompletableFuture()
                .thenApply(_ -> null));
    }

    public CompletableFuture<Void> saveContext(BattleContext context, Duration ttl) {
        return this.cache.runAsync(commands -> commands
                .set(PokeBattleKeys.battleContext(context.battleId()), context.toJson(), SetArgs.Builder.px(ttl.toMillis()))
                .toCompletableFuture()
                .thenApply(_ -> null));
    }

    public CompletableFuture<Optional<BattleContext>> context(UUID battleId) {
        return this.cache.runAsync(commands -> commands
                .get(PokeBattleKeys.battleContext(battleId))
                .toCompletableFuture()
                .thenApply(json -> Optional.ofNullable(json).map(BattleContext::fromJson)));
    }

    public CompletableFuture<Void> refreshContext(UUID battleId, Duration ttl) {
        return this.cache.runAsync(commands -> commands
                .pexpire(PokeBattleKeys.battleContext(battleId), ttl.toMillis())
                .toCompletableFuture()
                .thenApply(_ -> null));
    }

    public CompletableFuture<Void> deleteContext(UUID battleId) {
        return this.cache.runAsync(commands -> commands
                .del(PokeBattleKeys.battleContext(battleId))
                .toCompletableFuture()
                .thenApply(_ -> null));
    }
}
