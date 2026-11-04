package com.astralrealms.poke.battle;

import com.astralrealms.poke.battle.api.PokeBattleApi;
import com.astralrealms.poke.battle.api.service.BattleService;

class PokeBattleApiImpl implements PokeBattleApi {

    private final PokeBattle plugin;

    PokeBattleApiImpl(PokeBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public BattleService battles() {
        return this.plugin.battles();
    }
}
