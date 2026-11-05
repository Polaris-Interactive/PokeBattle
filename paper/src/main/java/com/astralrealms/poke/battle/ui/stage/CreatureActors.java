package com.astralrealms.poke.battle.ui.stage;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Puts creatures on a battle's stage. The only implementation is {@link PokeEntitiesActors}, loaded when PokeEntities
 * is: nothing else in PokeBattle names a PokeEntities type, so PokeBattle runs without it.
 */
public interface CreatureActors {

    /**
     * A creature of this species standing at {@code location}, facing its yaw, that only {@code viewer} sees. Called
     * on the location's region thread.
     *
     * @return null when it could not be spawned (no model, no blueprint...)
     */
    @Nullable Entity spawn(Player viewer, String species, Location location);

    /**
     * @return false when the model has no such animation
     */
    boolean animate(Entity creature, String animation);

    void remove(Entity creature);
}
