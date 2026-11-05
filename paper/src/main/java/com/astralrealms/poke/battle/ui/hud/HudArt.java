package com.astralrealms.poke.battle.ui.hud;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.key.Key;

/**
 * Battle HUD art, from {@code assets/battle-hud} and shipped in the {@code pokesmp} resource pack as flat item models
 * ({@code pokesmp:hud_*}, see {@code configuration/battle_hud.yml} of the pack). Each one is drawn as an item display,
 * sized by its transform, so the text of a button or card sits on top of it.
 */
@UtilityClass
public class HudArt {

    /** The action buttons: the action's art behind its label. */
    public static final Key FRAME_FIGHT = Key.key("pokesmp", "hud_action_fight");
    public static final Key FRAME_SWITCH = Key.key("pokesmp", "hud_action_switch");
    public static final Key FRAME_BAG = Key.key("pokesmp", "hud_action_bag");
    public static final Key FRAME_RUN = Key.key("pokesmp", "hud_action_run");

    /** Card frames: the player's creature (left) and the opponent's (flipped). */
    public static final Key FRAME_ALLY = Key.key("pokesmp", "hud_frame_ally");
    public static final Key FRAME_FOE = Key.key("pokesmp", "hud_frame_foe");

    /** The battle log panel. */
    public static final Key FRAME_LOG = Key.key("pokesmp", "hud_frame_log");

    /** List buttons (moves, team, bag): a plain strip behind the label. */
    public static final Key FRAME_LIST = Key.key("pokesmp", "hud_frame_list");

    /** The turn marker in front of the turn and timer line. */
    public static final Key TURN_ICON = Key.key("pokesmp", "hud_turn");

    /** The status strip on a card, one per Showdown status id. */
    private static final Map<String, Key> STATUS = Map.of(
            "brn", Key.key("pokesmp", "hud_status_brn"),
            "par", Key.key("pokesmp", "hud_status_par"),
            "psn", Key.key("pokesmp", "hud_status_psn"),
            "tox", Key.key("pokesmp", "hud_status_tox"),
            "slp", Key.key("pokesmp", "hud_status_slp"),
            "frz", Key.key("pokesmp", "hud_status_frz"),
            "fnt", Key.key("pokesmp", "hud_status_fnt"));

    /**
     * The status strip for a Showdown status id, or null when there is none to draw.
     */
    public static @Nullable Key statusItem(String statusId) {
        return STATUS.get(statusId);
    }

    /**
     * An item showing the given model, for an item display.
     */
    public static ItemStack item(Key model) {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.editMeta(meta -> meta.setItemModel(new NamespacedKey(model.namespace(), model.value())));
        return stack;
    }
}
