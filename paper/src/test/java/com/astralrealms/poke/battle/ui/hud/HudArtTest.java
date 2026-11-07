package com.astralrealms.poke.battle.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

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
    void aStatusHasItsItemAndHealthyHasNone() {
        assertThat(HudArt.statusItem("brn").asString()).isEqualTo("pokesmp:hud_status_brn");
        assertThat(HudArt.statusItem("par").asString()).isEqualTo("pokesmp:hud_status_par");
        assertThat(HudArt.statusItem("")).isNull();
        assertThat(HudArt.statusItem("unknown")).isNull();
    }
}
