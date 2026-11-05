package com.astralrealms.poke.battle.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.TextComponent;

class HudArtTest {

    @Test
    void theActionButtonsAreDistinctGlyphsOfTheBattleFont() {
        Set<Character> glyphs = Set.of(HudArt.FIGHT, HudArt.SWITCH, HudArt.BAG, HudArt.RUN);
        assertThat(glyphs).hasSize(4);
        assertThat(glyphs.stream().map(c -> (int) c).collect(Collectors.toSet()))
                .allMatch(code -> code >= 0xE900 && code <= 0xE903);

        TextComponent glyph = (TextComponent) HudArt.glyph(HudArt.FIGHT);
        assertThat(glyph.content()).isEqualTo(String.valueOf(HudArt.FIGHT));
        assertThat(glyph.font()).isEqualTo(HudArt.FONT);
        assertThat(HudArt.FONT.asString()).isEqualTo("pokesmp:battle_hud");
    }

    @Test
    void theOtherArtIsItemModelsOfThePack() {
        Set<Key> models = Set.of(HudArt.FRAME_ALLY, HudArt.FRAME_FOE, HudArt.FRAME_LOG, HudArt.FRAME_LIST,
                HudArt.TURN_ICON);
        assertThat(models).hasSize(5);
        assertThat(models).allMatch(key -> key.namespace().equals("pokesmp") && key.value().startsWith("hud_"));
    }

    @Test
    void aStatusHasItsItemAndHealthyHasNone() {
        assertThat(HudArt.statusItem("brn").asString()).isEqualTo("pokesmp:hud_status_brn");
        assertThat(HudArt.statusItem("par").asString()).isEqualTo("pokesmp:hud_status_par");
        assertThat(HudArt.statusItem("")).isNull();
        assertThat(HudArt.statusItem("unknown")).isNull();
    }
}
