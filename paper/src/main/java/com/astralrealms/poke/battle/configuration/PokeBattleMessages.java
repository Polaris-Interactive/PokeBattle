package com.astralrealms.poke.battle.configuration;

import com.astralrealms.core.configuration.MessageEnum;
import com.astralrealms.core.placeholder.wrapper.impl.component.ComponentWrapper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * {@code messages.yml}, in the order of the file.
 */
@Getter
@RequiredArgsConstructor
public enum PokeBattleMessages implements MessageEnum {
    RELOADED,
    UNEXPECTED_ERROR,
    UNKNOWN_SPECIES,
    BATTLE_ABORTED_BY_ADMIN,
    PLAYER_NOT_IN_BATTLE,
    ALREADY_IN_BATTLE,
    NO_USABLE_CREATURE,
    NO_BATTLE_WORKER,
    BATTLE_REFUSED,
    NOT_IN_BATTLE,
    WAITING_FOR_TURN,
    CHOICE_UNAVAILABLE,
    CHOICE_REFUSED,
    REOPEN_HINT,
    BATTLE_WON,
    BATTLE_LOST,
    BATTLE_TIE,
    BATTLE_ABORTED,
    SETTLEMENT_FAILED,
    EXPERIENCE_GAINED,
    LEVEL_UP,
    MOVE_LEARNED,
    MOVE_PENDING,
    FOE_NAME_WILD,
    LOG_WILD_APPEARS,
    LOG_SEND_OUT,
    LOG_TURN,
    LOG_MOVE,
    LOG_HP_ALLY,
    LOG_HP_FOE,
    LOG_DAMAGE_FROM,
    LOG_HEAL,
    LOG_FAINT,
    LOG_SUPER_EFFECTIVE,
    LOG_RESISTED,
    LOG_IMMUNE,
    LOG_CRIT,
    LOG_MISS,
    LOG_FAIL,
    LOG_STATUS,
    LOG_CURE_STATUS,
    LOG_BOOST,
    LOG_UNBOOST,
    LOG_CANT,
    DIALOG_TITLE,
    DIALOG_FIELD,
    DIALOG_FORCED_SWITCH,
    DIALOG_STATUS,
    MOVE_BUTTON,
    MOVE_BUTTON_DISABLED,
    MOVE_TOOLTIP,
    TEAM_BUTTON,
    TEAM_TITLE,
    MEMBER_BUTTON,
    BACK_BUTTON,
    ;

    @Setter
    private ComponentWrapper value;
}
