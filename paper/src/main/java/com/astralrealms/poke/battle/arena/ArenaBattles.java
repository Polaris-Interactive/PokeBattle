package com.astralrealms.poke.battle.arena;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.astralrealms.poke.battle.PokeBattle;
import com.astralrealms.poke.battle.api.event.BattleEndEvent;
import com.astralrealms.poke.battle.api.model.BattleRequest;
import com.astralrealms.poke.battle.api.model.BattleStartResult;
import com.astralrealms.poke.battle.command.PokeBattleCommand;
import com.astralrealms.poke.battle.configuration.PokeBattleMessages;
import com.astralrealms.poke.core.PokeCoreAPI;
import com.astralrealms.poke.core.mechanics.generation.GeneratedCreature;
import com.astralrealms.poke.core.mechanics.generation.GenerationOptions;
import com.polaris.instances.PolarisInstancesAPI;
import com.polaris.instances.api.event.InstanceEndEvent;
import com.polaris.instances.api.event.PlayerEnterInstanceEvent;
import com.polaris.instances.api.model.Instance;

/**
 * On an arena server: starts the battle an arena instance was created for when its player arrives, staged at the
 * template's {@code arena.stage-spawn}, and closes the arena once the battle ends (the player goes back to the server
 * that sent them). Only loaded with PolarisInstances on this server.
 * <p>
 * One battle per arena: a player who leaves and comes back finds their battle again (re-attach), not a new one.
 */
public class ArenaBattles implements Listener {

    private final PokeBattle plugin;
    /** Arenas whose battle was started. */
    private final Set<UUID> started = ConcurrentHashMap.newKeySet();

    public ArenaBattles(PokeBattle plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnter(PlayerEnterInstanceEvent event) {
        Instance instance = event.instance();
        Player player = event.player();
        String json = instance.metadata().get(ArenaBattle.METADATA_KEY);
        if (!instance.type().equals(ArenaBattle.INSTANCE_TYPE) || json == null
                || !instance.expectedPlayers().contains(player.getUniqueId())
                || this.plugin.battles().battleOf(player.getUniqueId()).isPresent()
                || !this.started.add(instance.id()))
            return;

        BattleRequest request;
        try {
            request = this.request(player, instance, ArenaBattle.fromJson(json));
        } catch (RuntimeException e) {
            this.plugin.getSLF4JLogger().error("Arena {}: its battle cannot be built from {}", instance.id(), json, e);
            this.fail(player, instance);
            return;
        }
        this.plugin.battles()
                .start(request)
                .thenAccept(result -> {
                    if (result == BattleStartResult.STARTED)
                        return;
                    PokeBattleCommand.report(player, result);
                    this.fail(player, instance);
                });
    }

    private BattleRequest request(Player player, Instance instance, ArenaBattle battle) {
        if (!BattleRequest.WILD_FORMAT.equals(battle.format()))
            throw new IllegalArgumentException("unsupported arena format " + battle.format());
        GeneratedCreature creature = PokeCoreAPI.get()
                .creation()
                .generate(battle.species(), battle.form(), battle.level(), GenerationOptions.DEFAULT);
        Location stage = instance.spawn(this.plugin.configuration().arenaOrDefault().stageSpawn())
                .orElse(player.getLocation());
        return BattleRequest.wild(player, PokeCoreAPI.get().creation().sign(creature), battle.captureFlags())
                .at(stage, instance.template());
    }

    private void fail(Player player, Instance instance) {
        PokeBattleMessages.ARENA_BATTLE_FAILED.message(player);
        PolarisInstancesAPI.get().end(instance, "battle did not start");
    }

    /**
     * The arena's battle is over: it closes after {@code arena.return-delay}, time to read the result.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBattleEnd(BattleEndEvent event) {
        Instance instance = PolarisInstancesAPI.get().findByPlayer(event.playerId()).orElse(null);
        if (instance == null || !instance.type().equals(ArenaBattle.INSTANCE_TYPE)
                || !this.started.contains(instance.id()))
            return;
        long delay = this.plugin.configuration().arenaOrDefault().returnDelay().toMillis();
        Bukkit.getAsyncScheduler().runDelayed(this.plugin,
                _ -> PolarisInstancesAPI.get().end(instance, "battle " + event.outcome().name().toLowerCase()),
                Math.max(1, delay), TimeUnit.MILLISECONDS);
    }

    @EventHandler
    public void onArenaEnd(InstanceEndEvent event) {
        this.started.remove(event.instance().id());
    }
}
