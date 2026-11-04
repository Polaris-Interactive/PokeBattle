package com.astralrealms.poke.battle.api.model;

import org.bukkit.entity.Player;

import com.astralrealms.poke.core.mechanics.generation.GeneratedCreature;

/**
 * A battle to start. Wild battles only for now: a player's party against one rolled creature. Trainer, gym and player
 * battles come with their formats.
 *
 * @param format a PokeLegends format id known to the workers ({@code polariswild}...)
 */
public record BattleRequest(String format, Player player, GeneratedCreature wild) {

    public static final String WILD_FORMAT = "polariswild";

    public static BattleRequest wild(Player player, GeneratedCreature creature) {
        return new BattleRequest(WILD_FORMAT, player, creature);
    }
}
