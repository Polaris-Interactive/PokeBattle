package com.astralrealms.poke.battle.ui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;

/**
 * The stage of every battle shown here, shared by what is drawn on it (the creatures, the HUD camera). Laid in
 * front of the player the first time their battle is shown and kept until it ends, so coming back to it
 * ({@code /combat}) shows the same place.
 */
public class BattleStages {

    private final Map<UUID, BattleStage> stages = new ConcurrentHashMap<>();

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
        this.stages.put(battleId, new BattleStage(origin, configuration.stage(),
                configuration.hud() == null ? null : configuration.hud().camera()));
    }

    public void remove(UUID battleId) {
        this.stages.remove(battleId);
    }

    public void clear() {
        this.stages.clear();
    }
}
