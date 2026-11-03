package com.astralrealms.poke.battle;

import com.astralrealms.core.paper.plugin.AstralPaperPlugin;
import com.astralrealms.poke.battle.configuration.PokeBattleMessages;

import lombok.Getter;

@Getter
public final class PokeBattle extends AstralPaperPlugin {

    @Override
    public void onEnable() {
        super.onEnable();

        // Configuration
        this.loadConfiguration();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @Override
    public void loadConfiguration() {
        this.loadEnum("messages.yml", PokeBattleMessages.class);
    }
}
