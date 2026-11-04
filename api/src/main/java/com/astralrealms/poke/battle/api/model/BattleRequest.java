package com.astralrealms.poke.battle.api.model;

import java.util.Set;

import org.bukkit.entity.Player;

import com.astralrealms.poke.core.mechanics.generation.SignedRoll;

/**
 * A battle to start. Wild battles only for now: a player's party against one rolled creature, which the player can
 * catch (the roll is redeemed by PokeCore, so the stored creature is exactly the one that appeared). Trainer, gym and
 * player battles come with their formats.
 *
 * @param format       a PokeSMP format id known to the workers ({@code polariswild}...)
 * @param wild         the creature as it was rolled when it appeared, signed by PokeCore
 * @param captureFlags facts about the encounter for ball rules ({@code night}, {@code cave}, {@code fished}...)
 */
public record BattleRequest(String format, Player player, SignedRoll wild, Set<String> captureFlags) {

    public static final String WILD_FORMAT = "polariswild";

    public BattleRequest {
        captureFlags = Set.copyOf(captureFlags);
    }

    public static BattleRequest wild(Player player, SignedRoll creature) {
        return wild(player, creature, Set.of());
    }

    public static BattleRequest wild(Player player, SignedRoll creature, Set<String> captureFlags) {
        return new BattleRequest(WILD_FORMAT, player, creature, captureFlags);
    }
}
