package com.astralrealms.poke.battle.api.model;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.core.mechanics.generation.SignedRoll;

/**
 * A battle to start. Wild battles only for now: a player's party against one rolled creature, which the player can
 * catch (the roll is redeemed by PokeCore, so the stored creature is exactly the one that appeared). Trainer, gym and
 * player battles come with their formats.
 *
 * @param format       a PokeSMP format id known to the workers ({@code polariswild}...)
 * @param wild         the creature as it was rolled when it appeared, signed by PokeCore
 * @param captureFlags facts about the encounter for ball rules ({@code night}, {@code cave}, {@code fished}...)
 * @param stage        where the battle takes place (feet of the player's side, facing the opponent), null to lay it
 *                     in front of the player
 * @param place        where a caught creature is recorded as met, null for the player's world
 */
public record BattleRequest(String format, Player player, SignedRoll wild, Set<String> captureFlags,
                            @Nullable Location stage, @Nullable String place) {

    public static final String WILD_FORMAT = "polariswild";

    public BattleRequest {
        captureFlags = Set.copyOf(captureFlags);
    }

    public static BattleRequest wild(Player player, SignedRoll creature) {
        return wild(player, creature, Set.of());
    }

    public static BattleRequest wild(Player player, SignedRoll creature, Set<String> captureFlags) {
        return new BattleRequest(WILD_FORMAT, player, creature, captureFlags, null, null);
    }

    /**
     * The same battle, staged at {@code stage} in {@code place} (an arena: its world means nothing to a caught
     * creature).
     */
    public BattleRequest at(Location stage, String place) {
        return new BattleRequest(this.format, this.player, this.wild, this.captureFlags, stage.clone(), place);
    }
}
