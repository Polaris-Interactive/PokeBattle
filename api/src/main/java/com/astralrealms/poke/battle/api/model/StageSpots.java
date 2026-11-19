package com.astralrealms.poke.battle.api.model;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

/**
 * Where the creatures of a staged battle stand, in place of the configured {@code stage.*} offsets: a gym's own marks
 * on its battlefield, further apart than an encounter in the wild. In the stage's world.
 *
 * @param ally        the player's creature (the first one in doubles)
 * @param foe         the opponent's creature (the first one in doubles)
 * @param allyPartner the player's second creature in doubles; null for the configured offset from {@code ally}
 * @param foePartner  the opponent's second creature in doubles; null for the configured offset from {@code foe}
 */
public record StageSpots(Location ally, Location foe, @Nullable Location allyPartner, @Nullable Location foePartner) {

    public StageSpots {
        if (ally == null || foe == null)
            throw new IllegalArgumentException("Both creature spots are needed");
        ally = ally.clone();
        foe = foe.clone();
        allyPartner = allyPartner == null ? null : allyPartner.clone();
        foePartner = foePartner == null ? null : foePartner.clone();
    }

    /**
     * The spots of a singles battle (a doubles battle puts the second creatures at the configured offsets).
     */
    public StageSpots(Location ally, Location foe) {
        this(ally, foe, null, null);
    }
}
