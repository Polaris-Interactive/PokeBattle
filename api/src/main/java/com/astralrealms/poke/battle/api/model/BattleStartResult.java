package com.astralrealms.poke.battle.api.model;

/**
 * Why a battle did or did not start.
 */
public enum BattleStartResult {
    STARTED,
    /** The player is already in a battle, here or on another server. */
    ALREADY_IN_BATTLE,
    /** The player's creatures are not loaded here, or none of them can fight. */
    NO_USABLE_CREATURE,
    /** No worker took the battle in time. */
    NO_WORKER,
    /** A worker refused it (unknown format, invalid team...). */
    REFUSED,
    ERROR,
    ;

    public boolean started() {
        return this == STARTED;
    }
}
