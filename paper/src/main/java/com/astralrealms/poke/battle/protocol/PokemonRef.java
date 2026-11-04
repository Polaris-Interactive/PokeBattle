package com.astralrealms.poke.battle.protocol;

import org.jetbrains.annotations.Nullable;

/**
 * A creature as the protocol names it: {@code p1a: 2} on the field (side, position, label) or {@code p1: 2} in a
 * request. The label is the set name PokeBattle gave the team member, never a display name.
 *
 * @param position {@code a}, {@code b}... on the field, empty in a request
 */
public record PokemonRef(String side, String position, String label) {

    public static @Nullable PokemonRef parse(@Nullable String value) {
        if (value == null)
            return null;
        int colon = value.indexOf(": ");
        if (colon < 2 || !value.startsWith("p"))
            return null;
        String prefix = value.substring(0, colon);
        String side = prefix.substring(0, 2);
        return new PokemonRef(side, prefix.substring(2), value.substring(colon + 2));
    }
}
