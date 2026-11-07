package com.astralrealms.poke.battle.ui;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;

/**
 * The stage of every battle shown here, shared by what is drawn on it (the creatures, the HUD camera). Laid in
 * front of the player the first time their battle is shown and kept until it ends, so coming back to it
 * ({@code /combat}) shows the same place.
 */
public class BattleStages {

    private final Map<UUID, BattleStage> stages = new ConcurrentHashMap<>();
    /** Battles staged in an arena rather than where they started. */
    private final Set<UUID> placed = ConcurrentHashMap.newKeySet();

    /**
     * The battle's stage, laid in front of {@code player} if it has none yet.
     */
    public BattleStage stage(Player player, UUID battleId, PokeBattleConfiguration configuration) {
        return this.stages.computeIfAbsent(battleId, _ -> new BattleStage(player.getLocation(),
                configuration.stage(), configuration.hud() == null ? null : configuration.hud().camera()));
    }

    /**
     * Lays the battle's stage at {@code origin} (an arena), before it is first shown.
     */
    public void place(UUID battleId, Location origin, PokeBattleConfiguration configuration) {
        this.placed.add(battleId);
        this.stages.put(battleId, new BattleStage(origin, configuration.stage(),
                configuration.hud() == null ? null : configuration.hud().camera()));
    }

    /**
     * The battle's stage, if it has been laid.
     */
    public @Nullable BattleStage find(UUID battleId) {
        return this.stages.get(battleId);
    }

    /**
     * Whether the battle is staged in an arena ({@link #place}) rather than where it started.
     */
    public boolean placed(UUID battleId) {
        return this.placed.contains(battleId);
    }

    public void remove(UUID battleId) {
        this.stages.remove(battleId);
        this.placed.remove(battleId);
    }

    public void clear() {
        this.stages.clear();
        this.placed.clear();
    }
}
