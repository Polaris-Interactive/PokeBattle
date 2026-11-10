package com.astralrealms.poke.battle.ui.hud;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.key.Key;

/**
 * The art of the battle overlay: glyphs of the pack's {@code pokesmp:hud_overlay} font, drawn on the screen itself.
 * Characters and sizes are those {@code pokeapi-importer/tools/build_battle_overlay.py} writes: keep both in step.
 */
enum OverlayGlyph {
    FRAME_ALLY('', 131),
    FRAME_FOE('', 131),
    LOG('', 169),
    UNDERLAY('', 28),
    STATUS_BRN('', 74),
    STATUS_PAR('', 74),
    STATUS_PSN('', 74),
    STATUS_TOX('', 74),
    STATUS_SLP('', 74),
    STATUS_FRZ('', 74),
    STATUS_FNT('', 74);

    static final Key FONT = Key.key("pokesmp", "hud_overlay");
    /** One glyph per species, at {@link #SPECIES_BASE} plus its dex number; each takes {@link #SPECIES_WIDTH}. */
    static final Key SPECIES_FONT = Key.key("pokesmp", "hud_species");
    static final int SPECIES_BASE = 0xE400;
    static final int SPECIES_WIDTH = 26;
    /** Spaces: {@code SPACE_PLUS + k} moves 2^k pixels right, {@code SPACE_MINUS + k} as many left, k below 10. */
    static final int SPACE_PLUS = 0xE100;
    static final int SPACE_MINUS = 0xE110;
    static final int SPACE_BITS = 10;
    /** Health bar pieces: {@code BAR_BASE + k} is 2^k pixels wide, k below 7; white, coloured by the palette. */
    static final int BAR_BASE = 0xE220;
    static final int BAR_BITS = 7;

    private static final Map<String, OverlayGlyph> STATUS = Map.of("brn", STATUS_BRN, "par", STATUS_PAR,
            "psn", STATUS_PSN, "tox", STATUS_TOX, "slp", STATUS_SLP, "frz", STATUS_FRZ, "fnt", STATUS_FNT);

    private final char character;
    private final int width;

    OverlayGlyph(char character, int width) {
        this.character = character;
        this.width = width;
    }

    char character() {
        return this.character;
    }

    /** Its art's width, in GUI pixels. The game moves on one pixel more. */
    int width() {
        return this.width;
    }

    /**
     * The status strip for a Showdown status id, or null when there is none to draw.
     */
    static @Nullable OverlayGlyph status(String statusId) {
        return STATUS.get(statusId);
    }
}
