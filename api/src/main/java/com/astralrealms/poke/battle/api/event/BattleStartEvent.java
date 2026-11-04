package com.astralrealms.poke.battle.api.event;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import lombok.Getter;

/**
 * A worker took a battle and it is now shown to the player. Called off the main thread.
 */
@Getter
public class BattleStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID battleId;
    private final Player player;
    private final String format;

    public BattleStartEvent(UUID battleId, Player player, String format) {
        super(true);
        this.battleId = battleId;
        this.player = player;
        this.format = format;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
