package com.astralrealms.poke.battle.ui;

import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

/**
 * How a player sees and plays their battle: the choice dialog and the chat ({@link DialogUi}), or the HUD drawn in
 * front of them. The battle calls it from the player's thread, in the order things happened.
 */
public interface BattleUi {

    /**
     * The battle started, or the player came back to it (rejoined, {@code /combat}). Does nothing if it is already
     * shown.
     */
    void open(Player player, FieldView field);

    /**
     * The field changed; {@code log} is what happened, in order.
     */
    void update(Player player, FieldView field, List<Component> log);

    /**
     * A choice to make.
     */
    void choose(Player player, ChoiceView view);

    /**
     * The choice left: nothing to do until the turn is played.
     */
    void waiting(Player player);

    /**
     * Battle {@code battleId} is over. Leaves alone what belongs to a later battle.
     */
    void close(Player player, UUID battleId);

    /**
     * The plugin is stopping: closes everything. Main thread.
     */
    void shutdown();
}
