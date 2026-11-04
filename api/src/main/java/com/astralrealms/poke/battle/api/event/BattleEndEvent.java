package com.astralrealms.poke.battle.api.event;

import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import com.astralrealms.poke.battle.api.model.BattleOutcome;

import lombok.Getter;

/**
 * A battle is over for a player and its outcome is settled (conditions and rewards written, or refused). The player
 * may be offline. Called off the main thread.
 *
 * @see #settled() false when PokeCore refused the settlement: nothing was written
 */
@Getter
public class BattleEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID battleId;
    private final UUID playerId;
    private final String format;
    private final BattleOutcome outcome;
    private final int turns;
    private final boolean settled;

    public BattleEndEvent(UUID battleId, UUID playerId, String format, BattleOutcome outcome, int turns,
                          boolean settled) {
        super(true);
        this.battleId = battleId;
        this.playerId = playerId;
        this.format = format;
        this.outcome = outcome;
        this.turns = turns;
        this.settled = settled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
