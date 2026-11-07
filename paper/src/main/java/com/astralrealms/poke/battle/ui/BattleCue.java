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
    /** The creature lost HP (an attack, poison, recoil...). */
    ALLY_DAMAGE,
    FOE_DAMAGE,
    /** A stat of the creature rose (not for a change of 0: "won't go any higher"). */
    ALLY_BOOST,
    FOE_BOOST,
    /** A stat of the creature fell. */
    ALLY_UNBOOST,
    FOE_UNBOOST,
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
            case "-damage" -> side(line, ALLY_DAMAGE, FOE_DAMAGE);
            case "-boost" -> changed(line) ? side(line, ALLY_BOOST, FOE_BOOST) : null;
            case "-unboost" -> changed(line) ? side(line, ALLY_UNBOOST, FOE_UNBOOST) : null;
            case "polaris-ball" -> CAPTURE;
            case "win", "tie" -> END;
            default -> null;
        };
    }

    /**
     * The id of the move a {@code move} line shows: null for another line, and for a move the simulator plays without
     * an animation ({@code [still]}: a charging turn...) or that had no target ({@code [notarget]}).
     */
    public static @Nullable String move(ProtocolLine line) {
        if (!line.type().equals("move") || line.has("still") || line.has("notarget"))
            return null;
        String id = ProtocolLine.toId(line.arg(1));
        return id.isEmpty() ? null : id;
    }

    /**
     * Whether a {@code -boost}/{@code -unboost} line changed the stat: the simulator sends 0 when it is already at
     * its limit.
     */
    private static boolean changed(ProtocolLine line) {
        return !line.arg(2).equals("0");
    }

    private static @Nullable BattleCue side(ProtocolLine line, BattleCue ally, BattleCue foe) {
        PokemonRef ref = PokemonRef.parse(line.arg(0));
        if (ref == null)
            return null;
        return ref.side().equals(Battle.PLAYER_SIDE) ? ally : foe;
    }
}
