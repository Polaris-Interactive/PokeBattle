package com.astralrealms.poke.battle.api.event;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import lombok.Getter;

/**
 * A player got back the battle they were in, after {@link BattleStartEvent}: they came back to this server, or this
 * server took it over from the one they left (re-attach). What a plugin put on the battle's stage for them (a trainer
 * at its spot) is to be put back. Called on whichever thread noticed it, async off the main thread.
 */
@Getter
public class BattleResumeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID battleId;
    private final Player player;
    private final String format;
    /** The request's tag, null when it had none. */
    private final @Nullable String tag;
    /**
     * Where the battle is staged on this server (the request's {@code stage}), null when it is laid in front of the
     * player: not staged, or staged in a world this server does not have.
     */
    private final @Nullable Location stage;

    public BattleResumeEvent(UUID battleId, Player player, String format, @Nullable String tag,
                             @Nullable Location stage) {
        super(!Bukkit.isPrimaryThread());
        this.battleId = battleId;
        this.player = player;
        this.format = format;
        this.tag = tag;
        this.stage = stage == null ? null : stage.clone();
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
