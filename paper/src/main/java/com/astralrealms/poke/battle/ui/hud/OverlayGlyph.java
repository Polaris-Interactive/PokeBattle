package com.astralrealms.poke.battle.ui.hud;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.core.api.overlay.OverlayFonts;
import com.astralrealms.poke.core.api.overlay.OverlayLine;
import com.astralrealms.poke.core.api.overlay.OverlayLine.Anchor;
import com.astralrealms.poke.core.api.overlay.OverlayPalette;

/**
 * The art of the battle overlay: glyphs of the pack's {@code pokesmp:hud_overlay} font ({@link OverlayFonts#FONT}),
 * drawn on the screen itself. Characters and sizes are those {@code pokeapi-importer/tools/build_battle_overlay.py}
 * writes: keep both in step. The shared glyphs (spaces, bars, species) are PokeCore's {@link OverlayFonts}.
 */
enum OverlayGlyph {
    FRAME_ALLY('\uE200', 131),
    FRAME_FOE('\uE201', 131),
    /** The log panel, stretched from the art to this width by the pack's builder ({@code LOG_SIZE}). */
    LOG('\uE202', 230),
    UNDERLAY('\uE203', 28),
    TURN('\uE204', 10),
    STATUS_BRN('\uE210', 74),
    STATUS_PAR('\uE211', 74),
    STATUS_PSN('\uE212', 74),
    STATUS_TOX('\uE213', 74),
    STATUS_SLP('\uE214', 74),
    STATUS_FRZ('\uE215', 74),
    STATUS_FNT('\uE216', 74);

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
     * Draws this glyph in its own colours.
     */
    void draw(OverlayLine line, Anchor anchor, int x, int dy) {
        line.art(anchor, x, dy, OverlayFonts.FONT, this.character, this.width, OverlayPalette.WHITE);
    }

    /**
     * The status strip for a Showdown status id, or null when there is none to draw.
     */
    static @Nullable OverlayGlyph status(String statusId) {
        return STATUS.get(statusId);
    }
}
