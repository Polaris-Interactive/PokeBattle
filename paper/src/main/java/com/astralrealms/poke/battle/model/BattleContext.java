package com.astralrealms.poke.battle.model;

import java.util.List;
import java.util.UUID;

import com.google.gson.Gson;

/**
 * What a shard needs to show and settle a battle besides the worker's packets, kept in Redis by the shard that started
 * it so another one can take it over (the player changed server, or the shard crashed). The player's creatures are
 * named by id, in label order: they are read back from the player's storage, which a battle never changes. The foes
 * are in label order too (a context written before trainers held whole wild creatures: their species, form and level
 * read the same).
 */
public record BattleContext(UUID battleId, String format, UUID playerId, List<UUID> allies,
                            List<Battle.Foe> foes, Battle.Options options) {

    private static final Gson GSON = new Gson();

    public BattleContext {
        allies = List.copyOf(allies);
        foes = List.copyOf(foes);
    }

    public static BattleContext of(Battle battle) {
        return new BattleContext(battle.id(), battle.format(), battle.playerId(),
                battle.allies().stream().map(ally -> ally.pokemon().id()).toList(),
                battle.foes(),
                battle.options());
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static BattleContext fromJson(String json) {
        return GSON.fromJson(json, BattleContext.class);
    }
}
