package com.astralrealms.poke.battle.api.model;

/**
 * How a battle ended for one player.
 */
public enum BattleOutcome {
    WON,
    /** Won by catching the opponent, which is now the player's. */
    CAPTURED,
    LOST,
    TIE,
    /** The player fled: no winner, no rewards, creatures keep their condition. */
    FLED,
    /** Stopped with no winner (left, aborted by an admin, worker failure): no rewards. */
    ABORTED,
}
