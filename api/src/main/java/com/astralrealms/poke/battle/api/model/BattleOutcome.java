package com.astralrealms.poke.battle.api.model;

/**
 * How a battle ended for one player.
 */
public enum BattleOutcome {
    WON,
    LOST,
    TIE,
    /** Stopped with no winner (left, aborted by an admin, worker failure): no rewards. */
    ABORTED,
}
