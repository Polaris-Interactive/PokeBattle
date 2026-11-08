package com.astralrealms.poke.battle.api.model;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.core.mechanics.generation.SignedRoll;

/**
 * A battle to start: a player's party against either one rolled wild creature, which the player can catch (the roll
 * is redeemed by PokeCore, so the stored creature is exactly the one that appeared), or an NPC trainer's team.
 * Exactly one of {@code wild} and {@code trainer} is set.
 *
 * @param format       a PokeSMP format id known to the workers ({@code polariswild}, {@code polaristrainer},
 *                     {@code polarisgym}...)
 * @param wild         the creature as it was rolled when it appeared, signed by PokeCore; null for a trainer
 * @param trainer      the NPC trainer; null for a wild creature
 * @param captureFlags facts about the encounter for ball rules ({@code night}, {@code cave}, {@code fished}...)
 * @param rules        what the player may do and the experience multiplier
 * @param tag          opaque to PokeBattle, given back by {@code BattleStartEvent} and {@code BattleEndEvent} on
 *                     whichever server fires them (it survives recovery); null for none
 * @param stage        where the battle takes place (feet of the player's side, facing the opponent), null to lay it
 *                     in front of the player
 * @param place        where a caught creature is recorded as met, null for the player's world
 */
public record BattleRequest(String format, Player player, @Nullable SignedRoll wild,
                            @Nullable TrainerOpponent trainer, Set<String> captureFlags, BattleRules rules,
                            @Nullable String tag, @Nullable Location stage, @Nullable String place) {

    public static final String WILD_FORMAT = "polariswild";
    public static final String TRAINER_FORMAT = "polaristrainer";
    public static final String GYM_FORMAT = "polarisgym";

    public BattleRequest {
        if ((wild == null) == (trainer == null))
            throw new IllegalArgumentException("A battle is against a wild creature or a trainer, not both or none");
        captureFlags = captureFlags == null ? Set.of() : Set.copyOf(captureFlags);
        if (rules == null)
            rules = wild != null ? BattleRules.WILD : BattleRules.TRAINER;
    }

    public static BattleRequest wild(Player player, SignedRoll creature) {
        return wild(player, creature, Set.of());
    }

    public static BattleRequest wild(Player player, SignedRoll creature, Set<String> captureFlags) {
        return new BattleRequest(WILD_FORMAT, player, creature, null, captureFlags, BattleRules.WILD, null, null,
                null);
    }

    /**
     * A battle against an NPC trainer.
     *
     * @param format {@link #TRAINER_FORMAT}, {@link #GYM_FORMAT}...
     */
    public static BattleRequest trainer(Player player, TrainerOpponent opponent, String format, BattleRules rules,
                                        @Nullable String tag) {
        return new BattleRequest(format, player, null, opponent, Set.of(), rules, tag, null, null);
    }

    /**
     * The same battle, staged at {@code stage} in {@code place} (an arena, or a fixed spot such as a gym's: its world
     * means nothing to a caught creature).
     */
    public BattleRequest at(Location stage, String place) {
        return new BattleRequest(this.format, this.player, this.wild, this.trainer, this.captureFlags, this.rules,
                this.tag, stage.clone(), place);
    }

    /**
     * The same battle with another tag.
     */
    public BattleRequest withTag(@Nullable String tag) {
        return new BattleRequest(this.format, this.player, this.wild, this.trainer, this.captureFlags, this.rules,
                tag, this.stage, this.place);
    }
}
