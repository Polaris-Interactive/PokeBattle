package com.astralrealms.poke.battle.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.astralrealms.poke.battle.ui.hud.OverlayLine.Anchor;
import com.astralrealms.poke.battle.ui.hud.OverlayLine.Run;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

class OverlayLineTest {

    private static final OverlayFont FONT = new OverlayFont(Map.of((int) 'A', 6, (int) 'b', 5, (int) ' ', 4));

    /** A piece as the game would draw it: where it starts from the line's origin, and its colour. */
    private record Piece(String text, int x, TextColor colour) {
    }

    /**
     * Walks the line as the game lays it out; returns its pieces (spaces left out) and, last, where the line ends.
     */
    private static List<Piece> layout(Component line, int[] end) {
        List<Piece> pieces = new ArrayList<>();
        int cursor = 0;
        for (Component child : line.children()) {
            String text = ((TextComponent) child).content();
            if (OverlayGlyph.FONT.equals(child.font()) && text.codePointAt(0) < 0xE200) {
                for (int code : text.codePoints().toArray())
                    cursor += code >= OverlayGlyph.SPACE_MINUS
                            ? -(1 << code - OverlayGlyph.SPACE_MINUS)
                            : 1 << code - OverlayGlyph.SPACE_PLUS;
                continue;
            }
            pieces.add(new Piece(text, cursor, child.color()));
            int code = text.codePointAt(0);
            if (OverlayGlyph.FONT.equals(child.font()) && code >= OverlayGlyph.BAR_BASE)
                cursor += (1 << code - OverlayGlyph.BAR_BASE) + 1;
            else if (OverlayGlyph.FONT.equals(child.font()))
                cursor += OverlayGlyph.FRAME_ALLY.width() + 1;
            else
                cursor += FONT.width(text, false);
        }
        end[0] = cursor;
        return pieces;
    }

    @Test
    void theLineEndsWhereItBeganAndPiecesSitWhereTheyWereAsked() {
        Component line = new OverlayLine(FONT)
                .glyph(Anchor.TOP_LEFT, 4, 4, OverlayGlyph.FRAME_ALLY)
                .bar(Anchor.TOP_LEFT, 41, 26, 84, OverlayPalette.BAR_EMPTY)
                .text(Anchor.TOP_RIGHT, -50, 12, List.of(new Run("AA b", OverlayPalette.WHITE, false)), false)
                .build();
        int[] end = new int[1];
        List<Piece> pieces = layout(line, end);

        // 0 wide: the game centres it on the middle of the screen whatever it says
        assertThat(end[0]).isZero();
        assertThat(line.shadowColor()).isEqualTo(ShadowColor.none());
        assertThat(pieces).extracting(Piece::x).containsExactly(4, 41, 41 + 64, 41 + 64 + 16, -50);
        // R = 77, G = 128 + dy, B = anchor << 5 | palette
        assertThat(pieces.get(0).colour()).isEqualTo(TextColor.color(77, 132, 0));
        assertThat(pieces.get(1).colour()).isEqualTo(TextColor.color(77, 154, OverlayPalette.BAR_EMPTY));
        assertThat(pieces.get(4).colour()).isEqualTo(TextColor.color(77, 140, 2 << 5));
    }

    @Test
    void aShadowIsADarkCopyOnePixelDownAndRightBehindTheText() {
        Component line = new OverlayLine(FONT)
                .text(Anchor.BOTTOM_RIGHT, -20, 30, List.of(new Run("A", OverlayPalette.WHITE, false)), true)
                .build();
        List<Piece> pieces = layout(line, new int[1]);
        // From the bottom, one pixel down is one less
        assertThat(pieces).containsExactly(
                new Piece("A", -19, TextColor.color(77, 128 + 29, 5 << 5 | OverlayPalette.SHADOW)),
                new Piece("A", -20, TextColor.color(77, 128 + 30, 5 << 5)));
    }

    @Test
    void textIsReadAsRunsInThePaletteAndWrappedOrCutToFit() {
        List<Run> runs = OverlayLine.runs(Component.text("AA ", NamedTextColor.RED)
                .append(Component.text("AAA AA", NamedTextColor.WHITE)));
        assertThat(runs).containsExactly(new Run("AA ", 13, false), new Run("AAA AA", OverlayPalette.WHITE, false));

        // 40 pixels: "AA AAA" is 34 and fits, the last word goes to the next row, without its space
        List<List<Run>> rows = OverlayLine.wrap(FONT, runs, 40);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).extracting(Run::text).containsExactly("AA", " ", "AAA");
        assertThat(rows.get(1)).extracting(Run::text).containsExactly("AA");

        assertThat(OverlayLine.clip(FONT, runs, 22)).extracting(Run::text).containsExactly("AA ", "A");
    }
}
