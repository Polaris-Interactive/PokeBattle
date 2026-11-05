package com.astralrealms.poke.battle.ui.hud;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/**
 * Battle HUD art: the PNGs from {@code assets/battle-hud}, shipped in the {@code pokesmp} resource pack as the
 * {@code pokesmp:battle_hud} font (one private-use character per image, see {@code configuration/battle_hud.yml}
 * of the pack). Each one is drawn by the client only, so it shows once the pack is loaded.
 */
@UtilityClass
public class HudArt {

    public static final Key FONT = Key.key("pokesmp", "battle_hud");

    /** Action buttons: the labels are part of the art. */
    public static final char FIGHT = '';
    public static final char SWITCH = '';
    public static final char BAG = '';
    public static final char RUN = '';

    /** The turn marker in front of the turn and timer line. */
    public static final char TURN = '';

    /** Card frames: the player's creature (left) and the opponent's (flipped). */
    public static final char INFO_ALLY = '';
    public static final char INFO_FOE = '';

    /** The battle log panel. */
    public static final char LOG = '';

    /** List buttons (moves, team, bag): a plain strip behind the label. */
    public static final char LIST_BUTTON = '';

    /** The status strip on a card, one per Showdown status id. */
    private static final Map<String, Character> STATUS = Map.of(
            "brn", '',
            "par", '',
            "psn", '',
            "tox", '',
            "slp", '',
            "frz", '',
            "fnt", '');

    /**
     * One image, drawn in the battle HUD font.
     */
    public static Component glyph(char glyph) {
        return Component.text(glyph).font(FONT);
    }

    /**
     * The status strip for a Showdown status id, or null when there is none to draw.
     */
    public static @Nullable Component status(String statusId) {
        Character glyph = STATUS.get(statusId);
        return glyph == null ? null : glyph(glyph);
    }
}
