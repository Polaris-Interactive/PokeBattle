package com.astralrealms.poke.battle.ui.hud;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.format.TextColor;

/**
 * The colours the battle overlay can draw: the pack's text shader replaces an overlay glyph's magic colour by one of
 * these, by index. Same order as {@code PALETTE} in {@code pokeapi-importer/tools/build_battle_overlay.py}.
 */
@UtilityClass
class OverlayPalette {

    static final int WHITE = 0;
    static final int BAR_EMPTY = 19;
    /** Behind text, one pixel down and right. */
    static final int SHADOW = 24;

    private static final int[] COLOURS = {
            0xFFFFFF, 0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00,
            0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55,
            0x26D971, 0xF2C94C, 0xEB5757, 0x3A3A3A, 0x7FD7D0, 0x5BE37D, 0x4FA59B, 0xD0D0D0,
            0x202020,
    };

    /**
     * The palette colour closest to {@code colour}; white for none.
     */
    static int index(@org.jetbrains.annotations.Nullable TextColor colour) {
        if (colour == null)
            return WHITE;
        int best = WHITE;
        long distance = Long.MAX_VALUE;
        for (int i = 0; i < COLOURS.length; i++) {
            long red = colour.red() - (COLOURS[i] >> 16 & 255);
            long green = colour.green() - (COLOURS[i] >> 8 & 255);
            long blue = colour.blue() - (COLOURS[i] & 255);
            long found = red * red + green * green + blue * blue;
            if (found < distance) {
                distance = found;
                best = i;
            }
        }
        return best;
    }
}
