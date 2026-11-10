package com.astralrealms.poke.battle.ui.hud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Builds the one line of text the battle overlay is: every piece (art glyph, text) in the magic colour the pack's
 * text shader turns into a place on the screen and a palette colour ({@code R = 77}, {@code G = 128 + dy},
 * {@code B = anchor << 5 | palette}, see {@code pokeapi-importer/tools/build_battle_overlay.py}).
 * <p>
 * A piece is placed sideways with space glyphs, {@code x} GUI pixels from its anchor (negative: to its left), and
 * {@code dy} pixels from the top of the screen (from the bottom for a bottom anchor) by the shader. The line ends
 * where it began, so it is 0 wide: the game centres it, and its origin is the middle of the screen whatever it says.
 * Pieces are drawn in the order they are added, later ones over earlier ones.
 */
final class OverlayLine {

    /** Where a piece is placed from. The order is the shader's. */
    enum Anchor { TOP_LEFT, TOP_CENTRE, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTRE, BOTTOM_RIGHT }

    /** A stretch of text in one look. */
    record Run(String text, int palette, boolean bold) {
    }

    private static final int MAGIC_RED = 77;
    private static final int MAGIC_GREEN = 128;
    private static final int MAX_DY = 127;
    private static final Key DEFAULT_FONT = Key.key("minecraft", "default");

    private final OverlayFont font;
    private final TextComponent.Builder line = Component.text();
    /** Where the next glyph would be drawn, in pixels from the line's origin. */
    private int cursor;

    OverlayLine(OverlayFont font) {
        this.font = font;
    }

    OverlayLine glyph(Anchor anchor, int x, int dy, OverlayGlyph glyph) {
        return this.art(anchor, x, dy, OverlayGlyph.FONT, String.valueOf(glyph.character()), glyph.width() + 1,
                OverlayPalette.WHITE);
    }

    /**
     * The species' icon; nothing for a dex number of 0 or less.
     */
    OverlayLine species(Anchor anchor, int x, int dy, int dexNo) {
        if (dexNo <= 0)
            return this;
        return this.art(anchor, x, dy, OverlayGlyph.SPECIES_FONT,
                Character.toString(OverlayGlyph.SPECIES_BASE + dexNo), OverlayGlyph.SPECIES_WIDTH + 1,
                OverlayPalette.WHITE);
    }

    /**
     * A bar {@code width} pixels wide in a palette colour.
     */
    OverlayLine bar(Anchor anchor, int x, int dy, int width, int palette) {
        int at = x;
        for (int bit = OverlayGlyph.BAR_BITS - 1; bit >= 0; bit--) {
            int piece = 1 << bit;
            while (width - (at - x) >= piece) {
                this.art(anchor, at, dy, OverlayGlyph.FONT, Character.toString(OverlayGlyph.BAR_BASE + bit), piece + 1,
                        palette);
                at += piece;
            }
        }
        return this;
    }

    /**
     * Text starting at {@code x}; with {@code shadow}, a dark copy one pixel down and right behind it (the game's own
     * shadow is off: it would stay where the line really is).
     */
    OverlayLine text(Anchor anchor, int x, int dy, List<Run> runs, boolean shadow) {
        if (shadow)
            this.runs(anchor, x + 1, dy + (bottom(anchor) ? -1 : 1), runs, true);
        return this.runs(anchor, x, dy, runs, false);
    }

    private OverlayLine runs(Anchor anchor, int x, int dy, List<Run> runs, boolean shadow) {
        this.move(x);
        for (Run run : runs) {
            if (run.text().isEmpty())
                continue;
            this.line.append(Component.text(run.text(), Style.style()
                    .font(DEFAULT_FONT)
                    .color(magic(anchor, dy, shadow ? OverlayPalette.SHADOW : run.palette()))
                    .decoration(TextDecoration.BOLD, run.bold())
                    .build()));
            this.cursor += this.font.width(run.text(), run.bold());
        }
        return this;
    }

    private OverlayLine art(Anchor anchor, int x, int dy, Key font, String glyph, int advance, int palette) {
        this.move(x);
        this.line.append(Component.text(glyph, Style.style().font(font).color(magic(anchor, dy, palette)).build()));
        this.cursor += advance;
        return this;
    }

    /**
     * The line, back at its origin, without the game's text shadow.
     */
    Component build() {
        this.move(0);
        return this.line.shadowColor(ShadowColor.none()).build();
    }

    int width(List<Run> runs) {
        return width(this.font, runs);
    }

    static int width(OverlayFont font, List<Run> runs) {
        int width = 0;
        for (Run run : runs)
            width += font.width(run.text(), run.bold());
        return width;
    }

    private void move(int to) {
        int by = to - this.cursor;
        if (by == 0)
            return;
        StringBuilder spaces = new StringBuilder();
        int base = by > 0 ? OverlayGlyph.SPACE_PLUS : OverlayGlyph.SPACE_MINUS;
        int left = Math.abs(by);
        int biggest = 1 << OverlayGlyph.SPACE_BITS - 1;
        while (left >= biggest) {
            spaces.appendCodePoint(base + OverlayGlyph.SPACE_BITS - 1);
            left -= biggest;
        }
        for (int bit = OverlayGlyph.SPACE_BITS - 2; bit >= 0; bit--)
            if ((left & 1 << bit) != 0)
                spaces.appendCodePoint(base + bit);
        this.line.append(Component.text(spaces.toString(), Style.style().font(OverlayGlyph.FONT).build()));
        this.cursor = to;
    }

    private static boolean bottom(Anchor anchor) {
        return anchor.ordinal() >= 3;
    }

    private static TextColor magic(Anchor anchor, int dy, int palette) {
        return TextColor.color(MAGIC_RED, MAGIC_GREEN + Math.clamp(dy, 0, MAX_DY), anchor.ordinal() << 5 | palette & 31);
    }

    // ---- Text ----

    /**
     * A component as runs of text, each in the palette colour closest to its own. Text in another font (an icon
     * glyph) is left out: the overlay only knows how wide the default font is.
     */
    static List<Run> runs(Component component) {
        List<Run> runs = new ArrayList<>();
        Deque<Style> styles = new ArrayDeque<>();
        styles.push(Style.empty());
        ComponentFlattener.basic().flatten(component, new FlattenerListener() {
            @Override
            public void pushStyle(Style style) {
                styles.push(styles.peek().merge(style));
            }

            @Override
            public void component(String text) {
                Style style = styles.peek();
                if (text.isEmpty() || style.font() != null && !style.font().equals(DEFAULT_FONT))
                    return;
                runs.add(new Run(text, OverlayPalette.index(style.color()),
                        style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE));
            }

            @Override
            public void popStyle(Style style) {
                styles.pop();
            }
        });
        return runs;
    }

    /**
     * The runs cut into rows at most {@code width} pixels wide, at spaces where there are some.
     */
    static List<List<Run>> wrap(OverlayFont font, List<Run> runs, int width) {
        List<List<Run>> rows = new ArrayList<>();
        List<Run> row = new ArrayList<>();
        int used = 0;
        for (Run run : runs) {
            // Words with the space before them, so a row never starts with one
            for (String word : run.text().split("(?= )")) {
                int size = font.width(word, run.bold());
                if (used > 0 && used + size > width) {
                    rows.add(row);
                    row = new ArrayList<>();
                    used = 0;
                    word = word.stripLeading();
                    size = font.width(word, run.bold());
                }
                if (word.isEmpty())
                    continue;
                row.add(new Run(word, run.palette(), run.bold()));
                used += size;
            }
        }
        if (!row.isEmpty())
            rows.add(row);
        return rows;
    }

    /**
     * The runs cut short to fit {@code width} pixels.
     */
    static List<Run> clip(OverlayFont font, List<Run> runs, int width) {
        List<Run> kept = new ArrayList<>();
        int used = 0;
        for (Run run : runs) {
            String text = run.text();
            while (!text.isEmpty() && used + font.width(text, run.bold()) > width)
                text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
            if (!text.isEmpty())
                kept.add(new Run(text, run.palette(), run.bold()));
            used += font.width(text, run.bold());
            if (text.length() < run.text().length())
                break;
        }
        return kept;
    }
}
