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
    /**
     * Who settles a battle's end. A claim is a lease ({@code settledBy}, {@code settleUntil}) until the settlement is
     * written and confirmed ({@code settled}): a shard that stops between its claim and the database leaves a claim
     * that another one (or itself, restarted) takes again once the lease is over. The database side is idempotent
     * (redeemed roll, versioned creatures), so settling twice writes once.
     */
    public enum Claim {
        /** This shard settles it. */
        CLAIMED,
        /** This shard settles it, after an earlier claim that was never confirmed: it may have been written. */
        RETAKEN,
        /** Another shard settled it. */
        SETTLED,
        /** Another shard is settling it (its lease runs): asked again once the lease is over. */
        BUSY,
    }

    // Only one shard settles a battle; a battle whose record expired has nobody else to race with.
    // ARGV: shard, now (ms), lease (ms). Returns 1 claimed, 2 retaken, 0 settled, -1 busy
    private static final String CLAIM_SETTLEMENT = """
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return 1
            end
            if redis.call('HEXISTS', KEYS[1], 'settled') == 1 then
              return 0
            end
            local now = tonumber(ARGV[2])
            local by = redis.call('HGET', KEYS[1], 'settledBy')
            if by and by ~= ARGV[1] then
              local lease = tonumber(redis.call('HGET', KEYS[1], 'settleUntil') or '0')
              if lease > now then
                return -1
              end
            end
            redis.call('HSET', KEYS[1], 'settledBy', ARGV[1], 'settleUntil', tostring(now + tonumber(ARGV[3])))
            if by then
              return 2
            end
            return 1""";
    private static final String CONFIRM_SETTLEMENT = """
            if redis.call('EXISTS', KEYS[1]) == 1 then
              redis.call('HSET', KEYS[1], 'settled', ARGV[1])
            end
            return 1""";

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
     * Claims the settlement of a battle's end for {@code lease}: past it, a claim never confirmed
     * ({@link #confirmSettlement}) is taken again.
     */
    public CompletableFuture<Claim> claimSettlement(UUID battleId, String shardId, Duration lease) {
        return this.cache.runAsync(commands -> commands
                .<Long>eval(CLAIM_SETTLEMENT, ScriptOutputType.INTEGER, new String[]{PokeBattleKeys.battle(battleId)},
                        shardId, Long.toString(System.currentTimeMillis()), Long.toString(lease.toMillis()))
                .toCompletableFuture()
                .thenApply(BattleRecords::claim));
    }

    static Claim claim(@Nullable Long result) {
        if (result == null)
            return Claim.BUSY;
        return switch (result.intValue()) {
            case 1 -> Claim.CLAIMED;
            case 2 -> Claim.RETAKEN;
            case 0 -> Claim.SETTLED;
            default -> Claim.BUSY;
        };
    }

    /**
     * The settlement this shard claimed is written: nobody takes it again.
     */
    public CompletableFuture<Void> confirmSettlement(UUID battleId, String shardId) {
        return this.cache.runAsync(commands -> commands
                .<Long>eval(CONFIRM_SETTLEMENT, ScriptOutputType.INTEGER, new String[]{PokeBattleKeys.battle(battleId)},
                        shardId)
                .toCompletableFuture()
                .thenApply(_ -> null));
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
