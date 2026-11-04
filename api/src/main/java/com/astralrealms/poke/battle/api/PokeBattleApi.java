package com.astralrealms.poke.battle.api;

import com.astralrealms.poke.battle.api.service.BattleService;

/**
 * Everything PokeBattle offers to other plugins.
 */
public interface PokeBattleApi {

    BattleService battles();
}
