package com.astralrealms.poke.battle.arena;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;

/**
 * A battle to start in an arena, carried in the arena instance's metadata ({@link #METADATA_KEY}) from the server
 * that asked for it to the arena server. The arena server rolls and signs the opponent itself: a roll signed by
 * another server could not be redeemed there unless every server shares {@code POKECORE_ROLL_SECRET}.
 * <p>
 * Wild battles only for now; trainer, gym and ranked battles add their own fields with their formats.
 *
 * @param format       a PokeSMP format id ({@code polariswild})
 * @param species      species of the wild opponent
 * @param form         its form, null for the base one
 * @param level        its level
 * @param captureFlags facts about the encounter for ball rules
 */
public record ArenaBattle(String format, String species, @Nullable String form, int level,
                          Set<String> captureFlags) {

    public static final String METADATA_KEY = "pokebattle.battle";

    private static final Gson GSON = new Gson();

    public ArenaBattle {
        captureFlags = captureFlags == null ? Set.of() : Set.copyOf(captureFlags);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static ArenaBattle fromJson(String json) {
        return GSON.fromJson(json, ArenaBattle.class);
    }
}
