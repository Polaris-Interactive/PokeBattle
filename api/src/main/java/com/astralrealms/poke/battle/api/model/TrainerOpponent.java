package com.astralrealms.poke.battle.api.model;

import java.util.List;

import com.astralrealms.poke.core.team.TrainerMember;

/**
 * An NPC trainer the player battles. Its team is completed by PokeCore when the battle starts (defaults rolled once,
 * {@code CreationService.complete}) and refused if invalid ({@code CreationService.validate}).
 *
 * @param name shown in the battle log (plain text); {@code |} and line breaks are left out
 * @param team 1 to 6 members, in order: the first leads
 * @param ai   the worker AI that plays it ({@code trainer}, {@code gym})
 */
public record TrainerOpponent(String name, List<TrainerMember> team, String ai) {

    public static final int MAX_TEAM = 6;

    public TrainerOpponent {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("A trainer needs a name");
        if (team == null || team.isEmpty() || team.size() > MAX_TEAM)
            throw new IllegalArgumentException("A trainer needs 1 to " + MAX_TEAM + " creatures");
        if (ai == null || ai.isBlank())
            throw new IllegalArgumentException("A trainer needs an AI");
        team = List.copyOf(team);
    }
}
