package com.astralrealms.poke.battle.ui.hud;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/**
 * Battle HUD art, from {@code assets/battle-hud} and shipped in the {@code pokesmp} resource pack.
 * <ul>
 *     <li>The action buttons are glyphs of the {@code pokesmp:battle_hud} font: their labels are part of the art and
 *     they are clickable, so they are text.</li>
 *     <li>Everything else is an item with a flat model ({@code pokesmp:hud_*}, see {@code configuration/battle_hud.yml}
 *     of the pack), drawn as an item display, so it is sized by its transform instead of a font height.</li>
 * </ul>
 * All of it is drawn by the client, so it shows once the pack is loaded.
 */
@UtilityClass
public class HudArt {

    public static final Key FONT = Key.key("pokesmp", "battle_hud");

    /** Action buttons: the labels are part of the art. */
    public static final char FIGHT = '';
    public static final char SWITCH = '';
    public static final char BAG = '';
    public static final char RUN = '';

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
     * One glyph of the battle font.
     */
    public static Component glyph(char glyph) {
        return Component.text(glyph).font(FONT);
    }

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
