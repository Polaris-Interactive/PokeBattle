package com.astralrealms.poke.battle.ui.hud;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

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

    /** The tooltip's panel, behind its text. */
    public static final Key TOOLTIP = Key.key("pokesmp", "hud_tooltip");

    /** The turn marker in front of the turn and timer line. */
    public static final Key TURN_ICON = Key.key("pokesmp", "hud_turn");

    /** The square behind a card's species icon. */
    public static final Key ICON_UNDERLAY = Key.key("pokesmp", "hud_icon_underlay");

    /** The back button's art, with a second state for a hovered one. */
    public static final Key BACK = Key.key("pokesmp", "hud_back");

    /** The status strip on a card, one per Showdown status id. */
    private static final Map<String, Key> STATUS = Map.of(
            "brn", Key.key("pokesmp", "hud_status_brn"),
            "par", Key.key("pokesmp", "hud_status_par"),
            "psn", Key.key("pokesmp", "hud_status_psn"),
            "tox", Key.key("pokesmp", "hud_status_tox"),
            "slp", Key.key("pokesmp", "hud_status_slp"),
            "frz", Key.key("pokesmp", "hud_status_frz"),
            "fnt", Key.key("pokesmp", "hud_status_fnt"));

    /** The type badges' font: one glyph per type, from U+E920 in this order (the pack's battle_hud.yml). */
    private static final Key TYPE_FONT = Key.key("pokesmp", "hud_types");
    private static final List<String> TYPES = List.of("normal", "fire", "water", "electric", "grass", "ice", "fighting",
            "poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy");
    private static final char FIRST_TYPE_GLYPH = '\uE920';

    /**
     * A type's badge, to draw inline in text; empty for an unknown type.
     */
    public static Component typeIcon(String typeId) {
        int index = TYPES.indexOf(typeId);
        if (index < 0)
            return Component.empty();
        // White leaves the glyph's own colours alone
        return Component.text(String.valueOf((char) (FIRST_TYPE_GLYPH + index)), NamedTextColor.WHITE).font(TYPE_FONT);
    }

    /** Art with a second state for a hovered button ({@code <model>_hover}). */
    private static final Set<Key> HOVERABLE = Set.of(FRAME_FIGHT, FRAME_SWITCH, FRAME_BAG, FRAME_RUN,
            FRAME_LIST, BACK);

    /**
     * A button's art as it looks hovered, or the art itself when it has no hover state.
     */
    public static Key hover(Key art) {
        return HOVERABLE.contains(art) ? Key.key(art.namespace(), art.value() + "_hover") : art;
    }

    /**
     * The status strip for a Showdown status id, or null when there is none to draw.
     */
    public static @Nullable Key statusItem(String statusId) {
        return STATUS.get(statusId);
    }

    /**
     * The species icon item (CraftEngine's {@code pokesmp:species/<name>}), or null for no species.
     */
    public static @Nullable Key speciesItem(String species) {
        return species == null || species.isBlank() ? null : Key.key("pokesmp", "species/" + species.toLowerCase(Locale.ROOT));
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
