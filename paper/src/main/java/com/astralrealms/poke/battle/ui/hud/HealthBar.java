package com.astralrealms.poke.battle.ui.hud;

import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Health bars and colours for the HUD, from {@code hud.bar} in {@code config.yml}.
 */
@UtilityClass
public class HealthBar {

    /**
     * {@code length} symbols: what is left in its colour, the rest in the empty one. A creature still standing shows
     * at least one.
     */
    public static Component render(int hp, int max, PokeBattleConfiguration.Bar bar) {
        int length = Math.max(1, bar.length());
        double ratio = max <= 0 ? 0 : Math.clamp((double) hp / max, 0, 1);
        int filled = (int) Math.round(ratio * length);
        if (hp > 0 && filled == 0)
            filled = 1;
        String color = ratio > 0.5 ? bar.high() : ratio > 0.2 ? bar.medium() : bar.low();
        return Component.text()
                .append(Component.text(bar.symbol().repeat(filled), color(color, NamedTextColor.GREEN)))
                .append(Component.text(bar.symbol().repeat(length - filled), color(bar.empty(), NamedTextColor.DARK_GRAY)))
                .build();
    }

    private static TextColor color(String hex, TextColor fallback) {
        TextColor color = TextColor.fromHexString(hex);
        return color == null ? fallback : color;
    }

    /**
     * {@code #AARRGGBB}, or {@code #RRGGBB} for an opaque one.
     *
     * @throws IllegalArgumentException if it is neither
     */
    public static int argb(String hex) {
        String digits = hex.startsWith("#") ? hex.substring(1) : hex;
        if (digits.length() != 6 && digits.length() != 8)
            throw new IllegalArgumentException("Not a #RRGGBB or #AARRGGBB colour: " + hex);
        long value = Long.parseLong(digits, 16);
        return (int) (digits.length() == 6 ? value | 0xFF000000L : value);
    }
}
