package com.astralrealms.poke.battle.api.model;

import org.bukkit.Location;

/**
 * Where the two creatures of a staged battle stand, in place of the configured {@code stage.ally} / {@code stage.foe}
 * offsets: a gym's own marks on its battlefield, further apart than an encounter in the wild. In the stage's world.
 *
 * @param ally the player's creature
 * @param foe  the opponent's creature
 */
public record StageSpots(Location ally, Location foe) {

    public StageSpots {
        if (ally == null || foe == null)
            throw new IllegalArgumentException("Both creature spots are needed");
        ally = ally.clone();
        foe = foe.clone();
    }
}
