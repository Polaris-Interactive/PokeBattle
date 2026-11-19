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

    /**
     * The position's letter: {@code a} for slot 0, {@code b} for slot 1...
     */
    public static String position(int slot) {
        return String.valueOf((char) ('a' + slot));
    }

    /**
     * The 0-based slot of its position on the field ({@code a} is 0, {@code b} is 1), -1 in a request.
     */
    public int slot() {
        if (this.position.length() != 1)
            return -1;
        char letter = this.position.charAt(0);
        return letter >= 'a' && letter <= 'z' ? letter - 'a' : -1;
    }

    /**
     * Its place on the field: {@code p1a}, {@code p2b}...
     */
    public String key() {
        return this.side + this.position;
    }
}
