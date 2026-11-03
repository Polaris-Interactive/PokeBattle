package com.astralrealms.poke.battle.configuration;

import com.astralrealms.core.configuration.MessageEnum;
import com.astralrealms.core.placeholder.wrapper.impl.component.ComponentWrapper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@RequiredArgsConstructor
public enum PokeBattleMessages implements MessageEnum {
    // Admin
    RELOADED,
    UNEXPECTED_ERROR,
    ;

    @Setter
    private ComponentWrapper value;
}
