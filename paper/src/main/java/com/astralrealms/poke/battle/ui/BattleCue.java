package com.astralrealms.poke.battle.ui;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.battle.model.Battle;
import com.astralrealms.poke.battle.protocol.PokemonRef;
import com.astralrealms.poke.battle.protocol.ProtocolLine;

/**
 * Something in the battle worth showing on stage (a camera shot, later an effect), seen from the player's side.
 */
public enum BattleCue {
    ALLY_SWITCH,
    FOE_SWITCH,
    ALLY_MOVE,
    FOE_MOVE,
    ALLY_FAINT,
    FOE_FAINT,
    CAPTURE,
    END;

    /**
     * Its key in {@code config.yml}: {@code ally-move}...
     */
    public String key() {
        return this.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * @return null for a line with nothing to show
     */
    public static @Nullable BattleCue of(ProtocolLine line) {
        return switch (line.type()) {
            case "switch", "drag" -> side(line, ALLY_SWITCH, FOE_SWITCH);
            case "move" -> side(line, ALLY_MOVE, FOE_MOVE);
            case "faint" -> side(line, ALLY_FAINT, FOE_FAINT);
            case "polaris-ball" -> CAPTURE;
            case "win", "tie" -> END;
            default -> null;
        };
    }

    private static @Nullable BattleCue side(ProtocolLine line, BattleCue ally, BattleCue foe) {
        PokemonRef ref = PokemonRef.parse(line.arg(0));
        if (ref == null)
            return null;
        return ref.side().equals(Battle.PLAYER_SIDE) ? ally : foe;
    }
}
