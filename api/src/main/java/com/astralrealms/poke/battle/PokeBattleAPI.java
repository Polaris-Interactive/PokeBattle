package com.astralrealms.poke.battle;

import org.jetbrains.annotations.ApiStatus;

import com.astralrealms.poke.battle.api.PokeBattleApi;

import lombok.experimental.UtilityClass;

/**
 * Entry point for other plugins: {@code PokeBattleAPI.get().battles()...}. Available once PokeBattle is enabled.
 */
@UtilityClass
public class PokeBattleAPI {

    private static PokeBattleApi api;

    public static PokeBattleApi get() {
        if (api == null)
            throw new IllegalStateException("PokeBattle is not enabled");
        return api;
    }

    @ApiStatus.Internal
    public static void initialize(PokeBattleApi instance) {
        api = instance;
    }
}
