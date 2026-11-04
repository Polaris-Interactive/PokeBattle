package com.astralrealms.poke.battle.service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.astralrealms.core.cache.CacheService;
import com.astralrealms.poke.battle.network.PokeBattleKeys;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;

/**
 * The per-player battle lock, network-wide: {@code pokebattle:player:<uuid>:battle} holds the battle id. A shard
 * taking over a battle (re-attach) reads it; only the battle that holds it may refresh or release it.
 */
public class BattleLocks {

    private static final String REFRESH = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 0""";
    private static final String RELEASE = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0""";

    private final CacheService cache;

    public BattleLocks(CacheService cache) {
        this.cache = cache;
    }

    /**
     * @return false if the player is already in a battle
     */
    public CompletableFuture<Boolean> acquire(UUID playerId, UUID battleId, Duration ttl) {
        return this.cache.runAsync(commands -> commands
                .set(PokeBattleKeys.playerBattle(playerId), battleId.toString(), SetArgs.Builder.nx().px(ttl.toMillis()))
                .toCompletableFuture()
                .thenApply("OK"::equals));
    }

    public CompletableFuture<Boolean> refresh(UUID playerId, UUID battleId, Duration ttl) {
        return this.cache.runAsync(commands -> commands
                .<Long>eval(REFRESH, ScriptOutputType.INTEGER, new String[]{PokeBattleKeys.playerBattle(playerId)},
                        battleId.toString(), Long.toString(ttl.toMillis()))
                .toCompletableFuture()
                .thenApply(result -> result != null && result == 1L));
    }

    public CompletableFuture<Boolean> release(UUID playerId, UUID battleId) {
        return this.cache.runAsync(commands -> commands
                .<Long>eval(RELEASE, ScriptOutputType.INTEGER, new String[]{PokeBattleKeys.playerBattle(playerId)},
                        battleId.toString())
                .toCompletableFuture()
                .thenApply(result -> result != null && result == 1L));
    }

    public CompletableFuture<Optional<UUID>> current(UUID playerId) {
        return this.cache.runAsync(commands -> commands
                .get(PokeBattleKeys.playerBattle(playerId))
                .toCompletableFuture()
                .thenApply(value -> Optional.ofNullable(value).map(UUID::fromString)));
    }
}
