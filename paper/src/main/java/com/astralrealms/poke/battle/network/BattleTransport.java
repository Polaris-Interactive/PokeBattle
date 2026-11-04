package com.astralrealms.poke.battle.network;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import org.jetbrains.annotations.Nullable;

import com.astralrealms.core.messaging.MessagingService;
import com.astralrealms.poke.battle.network.packet.BattleChoicePacket;
import com.astralrealms.poke.battle.network.packet.BattleEndPacket;
import com.astralrealms.poke.battle.network.packet.BattleStartPacket;
import com.astralrealms.poke.battle.network.packet.BattleStartReplyPacket;

/**
 * This server's side of the battle protocol: starts go to the shared start queue (the first worker with room takes
 * it), everything after to the worker that took the battle.
 */
public class BattleTransport {

    private final MessagingService messaging;

    public BattleTransport(MessagingService messaging) {
        this.messaging = messaging;
    }

    /**
     * Publishes a start, again with the same battle id while no worker answers (a worker that already took it drops
     * the copy).
     *
     * @return the worker's reply, or null when none answered in time
     */
    public CompletableFuture<@Nullable BattleStartReplyPacket> start(BattleStartPacket packet, Duration timeout,
                                                                     int attempts) {
        return this.messaging.sendWithReply(PokeBattleExchanges.START, packet, timeout)
                .<BattleStartReplyPacket>thenApply(reply -> {
                    if (reply instanceof BattleStartReplyPacket startReply)
                        return startReply;
                    throw new IllegalStateException("Unexpected start reply " + reply.getClass().getSimpleName());
                })
                .exceptionallyCompose(throwable -> {
                    Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
                    if (!(cause instanceof TimeoutException))
                        return CompletableFuture.failedFuture(cause);
                    if (attempts <= 1)
                        return CompletableFuture.completedFuture(null);
                    return this.start(packet, timeout, attempts - 1);
                });
    }

    public CompletableFuture<Void> choose(String workerId, UUID battleId, String side, int rqid, String choice) {
        return this.messaging.send(PokeBattleExchanges.worker(workerId),
                new BattleChoicePacket(battleId, side, rqid, choice));
    }

    /**
     * @param result {@code p1}, {@code p2}... for that side's win, {@code tie}, or {@code aborted}
     */
    public CompletableFuture<Void> end(String workerId, UUID battleId, String result, String reason) {
        return this.messaging.send(PokeBattleExchanges.worker(workerId), new BattleEndPacket(battleId, result, reason));
    }
}
