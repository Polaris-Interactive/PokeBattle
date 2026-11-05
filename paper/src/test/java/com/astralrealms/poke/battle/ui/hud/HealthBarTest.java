package com.astralrealms.poke.battle.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

class HealthBarTest {

    private static final PokeBattleConfiguration.Bar BAR = new PokeBattleConfiguration.Bar(10, "|", "#00FF00",
            "#FFFF00", "#FF0000", "#333333");

    private static TextComponent part(Component bar, int index) {
        return (TextComponent) bar.children().get(index);
    }

    @Test
    void fillsWithWhatIsLeftInTheColourOfItsShare() {
        Component full = HealthBar.render(50, 50, BAR);
        assertThat(part(full, 0).content()).isEqualTo("||||||||||");
        assertThat(part(full, 0).color()).isEqualTo(TextColor.color(0x00FF00));
        assertThat(part(full, 1).content()).isEmpty();

        Component half = HealthBar.render(20, 50, BAR);
        assertThat(part(half, 0).content()).isEqualTo("||||");
        assertThat(part(half, 0).color()).isEqualTo(TextColor.color(0xFFFF00));
        assertThat(part(half, 1).content()).isEqualTo("||||||");
        assertThat(part(half, 1).color()).isEqualTo(TextColor.color(0x333333));

        assertThat(part(HealthBar.render(5, 50, BAR), 0).color()).isEqualTo(TextColor.color(0xFF0000));
    }

    @Test
    void aStandingCreatureShowsAtLeastOneAndAFaintedOneNone() {
        assertThat(part(HealthBar.render(1, 500, BAR), 0).content()).isEqualTo("|");
        assertThat(part(HealthBar.render(0, 500, BAR), 0).content()).isEmpty();
        // No max known yet: an empty bar rather than a division by zero
        assertThat(part(HealthBar.render(0, 0, BAR), 1).content()).isEqualTo("||||||||||");
    }

    @Test
    void readsArgbAndOpaqueRgb() {
        assertThat(HealthBar.argb("#E026D971")).isEqualTo(0xE026D971);
        assertThat(HealthBar.argb("#26D971")).isEqualTo(0xFF26D971);
        assertThatThrownBy(() -> HealthBar.argb("#123")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HealthBar.argb("#GGGGGG")).isInstanceOf(IllegalArgumentException.class);
    }
}
