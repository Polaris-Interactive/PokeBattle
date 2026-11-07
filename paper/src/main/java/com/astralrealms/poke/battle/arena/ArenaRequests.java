package com.astralrealms.poke.battle.arena;

import java.util.Map;

import org.bukkit.entity.Player;

import com.astralrealms.poke.battle.PokeBattle;
import com.astralrealms.poke.battle.configuration.PokeBattleMessages;
import com.polaris.instances.bridge.PolarisInstancesBridgeAPI;
import com.polaris.instances.bridge.api.model.InstanceRequest;

/**
 * Sends players to battle in an arena: asks PolarisInstances for an instance of the arena template, with the battle in
 * its metadata. The arena server starts the battle when the player arrives ({@link ArenaBattles}). Only loaded with
 * PolarisInstances-Bridge on this server.
 */
public class ArenaRequests {

    private final PokeBattle plugin;

    public ArenaRequests(PokeBattle plugin) {
        this.plugin = plugin;
    }

    /**
     * @param template the arena's template, within the {@link ArenaBattle#INSTANCE_TYPE} type
     */
    public void request(Player player, String template, ArenaBattle battle) {
        if (this.plugin.battles().battleOf(player.getUniqueId()).isPresent()) {
            PokeBattleMessages.ALREADY_IN_BATTLE.message(player);
            return;
        }
        PokeBattleMessages.ARENA_REQUESTING.message(player);
        PolarisInstancesBridgeAPI.get()
                .request(InstanceRequest.forPlayer(ArenaBattle.INSTANCE_TYPE, template, player.getUniqueId())
                        .withMetadata(Map.of(ArenaBattle.METADATA_KEY, battle.toJson())))
                .whenComplete((ticket, throwable) -> {
                    if (throwable != null) {
                        this.plugin.getSLF4JLogger().warn("No arena {} for {}: {}", template, player.getName(),
                                throwable.getMessage());
                        PokeBattleMessages.ARENA_UNAVAILABLE.message(player);
                        return;
                    }
                    this.plugin.debug("Arena {} for {} is instance {} on {}", template, player.getName(),
                            ticket.instanceId(), ticket.serverName());
                });
    }
}
