package com.astralrealms.poke.battle.ui.stage;

import java.time.Duration;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.astralrealms.poke.battle.ui.BattleStage;
import com.astralrealms.poke.entities.PokeEntitiesAPI;
import com.astralrealms.poke.entities.api.model.CreatureSpawnResult;

/**
 * Battle creatures as PokeEntities creatures only the player sees (no AI, cannot be hurt). Only created when
 * PokeEntities is enabled.
 */
public class PokeEntitiesActors implements CreatureActors {

    private final Logger logger;

    public PokeEntitiesActors(Logger logger) {
        this.logger = logger;
    }

    @Override
    public @Nullable Entity spawn(Player viewer, String species, Location location) {
        CreatureSpawnResult result = PokeEntitiesAPI.get().creatures().spawnFor(viewer, species, location);
        if (!result.spawned()) {
            this.logger.warn("No battle creature for species {}: {}", species, result.status());
            return null;
        }
        return result.entity();
    }

    @Override
    public boolean animate(Entity creature, String animation) {
        return PokeEntitiesAPI.get().creatures().animate(creature, animation);
    }

    @Override
    public Optional<Duration> marker(Entity creature, String animation, String marker) {
        return PokeEntitiesAPI.get().creatures().marker(creature, animation, marker);
    }

    @Override
    public void remove(Entity creature) {
        PokeEntitiesAPI.get().creatures().remove(creature.getUniqueId());
    }

    @Override
    public void flash(Entity creature) {
        PokeEntitiesAPI.get().creatures().flash(creature);
    }

    @Override
    public BattleStage.@Nullable Body size(Entity creature) {
        return PokeEntitiesAPI.get().creatures().size(creature)
                .map(size -> new BattleStage.Body(size.height(), size.width()))
                .orElse(null);
    }
}
