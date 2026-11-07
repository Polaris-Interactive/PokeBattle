package com.astralrealms.poke.battle.ui.stage;

import java.time.Duration;
import java.util.Optional;

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

    /**
     * When {@code marker} comes in the creature's {@code animation}, from its start ({@code release}: the moment an
     * attack lets go).
     *
     * @return empty when the animation has no such marker
     */
    Optional<Duration> marker(Entity creature, String animation, String marker);

    void remove(Entity creature);

    /**
     * Flashes the creature red for a moment, as when it is hurt.
     */
    void flash(Entity creature);
}
