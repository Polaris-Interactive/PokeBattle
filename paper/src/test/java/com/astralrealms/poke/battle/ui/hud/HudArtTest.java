package com.astralrealms.poke.battle.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.bukkit.Color;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.key.Key;

class HudArtTest {

    @Test
    void theActionButtonsHaveTheirOwnArt() {
        Set<Key> models = Set.of(HudArt.FRAME_FIGHT, HudArt.FRAME_SWITCH, HudArt.FRAME_BAG, HudArt.FRAME_RUN);
        assertThat(models).hasSize(4);
        assertThat(models).allMatch(key -> key.namespace().equals("pokesmp") && key.value().startsWith("hud_action_"));
    }

    @Test
    void theOtherArtIsItemModelsOfThePack() {
        Set<Key> models = Set.of(HudArt.FRAME_ALLY, HudArt.FRAME_FOE, HudArt.FRAME_LOG, HudArt.FRAME_LIST,
                HudArt.TURN_ICON);
        assertThat(models).hasSize(5);
        assertThat(models).allMatch(key -> key.namespace().equals("pokesmp") && key.value().startsWith("hud_"));
    }

    @Test
    void theBackButtonHasAHoverStateAndTheIconSlotsHaveUnderlay() {
        assertThat(HudArt.hover(HudArt.BACK).asString()).isEqualTo("pokesmp:hud_back_hover");
        assertThat(HudArt.ICON_UNDERLAY.asString()).isEqualTo("pokesmp:hud_icon_underlay");
    }

    @Test
    void theMoveStripHasAHoverStateLikeTheListStrip() {
        assertThat(HudArt.FRAME_MOVE.asString()).isEqualTo("pokesmp:hud_frame_move");
        assertThat(HudArt.hover(HudArt.FRAME_MOVE).asString()).isEqualTo("pokesmp:hud_frame_move_hover");
    }

    @Test
    void aMoveIsTintedWithItsTypeColourElseOther() {
        Map<String, String> colours = Map.of("other", "#7C7F86", "fire", "#EE8130", "water", "6390F0");
        assertThat(HudArt.moveColour(colours, "fire", false)).isEqualTo(Color.fromRGB(0xEE8130));
        assertThat(HudArt.moveColour(colours, "water", false)).isEqualTo(Color.fromRGB(0x6390F0));
        assertThat(HudArt.moveColour(colours, "unknown", false)).isEqualTo(Color.fromRGB(0x7C7F86));
        assertThat(HudArt.moveColour(colours, "fire", true)).isEqualTo(Color.fromRGB(0x7C7F86));
    }

    @Test
    void noColoursMeansNoTint() {
        assertThat(HudArt.moveColour(null, "fire", false)).isNull();
        assertThat(HudArt.moveColour(Map.of(), "fire", false)).isNull();
        assertThat(HudArt.moveColour(Map.of("fire", "#EE8130"), "ice", false)).isNull();
    }

    @Test
    void aStatusHasItsItemAndHealthyHasNone() {
        assertThat(HudArt.statusItem("brn").asString()).isEqualTo("pokesmp:hud_status_brn");
        assertThat(HudArt.statusItem("par").asString()).isEqualTo("pokesmp:hud_status_par");
        assertThat(HudArt.statusItem("")).isNull();
        assertThat(HudArt.statusItem("unknown")).isNull();
    }
}
