package com.astralrealms.poke.battle.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.TextComponent;

class HudArtTest {

    @Test
    void eachImageHasItsOwnCharacterInTheBattleFont() {
        Set<Character> glyphs = Set.of(HudArt.FIGHT, HudArt.SWITCH, HudArt.BAG, HudArt.RUN, HudArt.TURN);
        assertThat(glyphs).hasSize(5);
        assertThat(glyphs.stream().map(c -> (int) c).collect(Collectors.toSet()))
                .allMatch(code -> code >= 0xE900 && code <= 0xE90B);
    }

    @Test
    void aGlyphIsDrawnInTheBattleFont() {
        TextComponent glyph = (TextComponent) HudArt.glyph(HudArt.FIGHT);
        assertThat(glyph.content()).isEqualTo(String.valueOf(HudArt.FIGHT));
        assertThat(glyph.font()).isEqualTo(HudArt.FONT);
        assertThat(HudArt.FONT.asString()).isEqualTo("pokesmp:battle_hud");
    }
}
