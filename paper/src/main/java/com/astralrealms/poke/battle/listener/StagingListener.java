package com.astralrealms.poke.battle.listener;

import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.battle.PokeBattle;
import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;
import com.astralrealms.poke.battle.ui.BattleStage;

/**
 * Battles fought where they started: a ring of particles around the stage that only the player sees, and a barrier
 * that keeps them inside it ({@code staging} in {@code config.yml}). Arena battles have neither: the arena is theirs.
 */
public class StagingListener implements Listener {

    /** Blocks up or down a ring point looks for the ground. */
    private static final int GROUND_SEARCH = 3;

    private final PokeBattle plugin;
    private @Nullable Particle particle;

    public StagingListener(PokeBattle plugin) {
        this.plugin = plugin;
    }

    /**
     * Draws the rings every {@code staging.interval} (read at enable); the rest of {@code staging} is read each time.
     */
    public void start() {
        long interval = Math.max(1, this.plugin.configuration().stagingOrDefault().interval().toMillis() / 50);
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, _ -> this.draw(), interval, interval);
    }

    private void draw() {
        PokeBattleConfiguration.Staging staging = this.plugin.configuration().stagingOrDefault();
        if (!staging.ring())
            return;
        Particle particle = this.particle(staging.particle());
        if (particle == null)
            return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            BattleStage stage = this.stage(player);
            if (stage == null)
                continue;
            Location centre = stage.at(staging.centre());
            if (centre.getWorld() == null || !centre.getWorld().equals(player.getWorld()))
                continue;
            int points = Math.max(8, staging.points());
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points;
                Location point = centre.clone().add(Math.cos(angle) * staging.radius(), 0,
                        Math.sin(angle) * staging.radius());
                player.spawnParticle(particle, ground(point).add(0, 0.1, 0), 1, 0, 0, 0, 0);
            }
        }
    }

    /**
     * A player fighting where they started cannot walk out of the ring; walking back in is always allowed.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition())
            return;
        PokeBattleConfiguration.Staging staging = this.plugin.configuration().stagingOrDefault();
        if (!staging.barrier())
            return;
        Player player = event.getPlayer();
        BattleStage stage = this.stage(player);
        if (stage == null)
            return;
        Location centre = stage.at(staging.centre());
        Location to = event.getTo();
        if (!to.getWorld().equals(centre.getWorld()))
            return;
        double radius = staging.radius();
        double after = flatDistanceSquared(to, centre);
        if (after <= radius * radius || after <= flatDistanceSquared(event.getFrom(), centre))
            return;
        Location back = event.getFrom().clone();
        back.setYaw(to.getYaw());
        back.setPitch(to.getPitch());
        event.setTo(back);
    }

    /**
     * The stage of the player's battle, when it is fought where it started and laid already.
     */
    private @Nullable BattleStage stage(Player player) {
        UUID battleId = this.plugin.battles().battleOf(player.getUniqueId()).orElse(null);
        if (battleId == null || this.plugin.stages().placed(battleId))
            return null;
        return this.plugin.stages().find(battleId);
    }

    private @Nullable Particle particle(String name) {
        if (this.particle != null && this.particle.name().equalsIgnoreCase(name))
            return this.particle;
        try {
            Particle found = Particle.valueOf(name.toUpperCase(Locale.ROOT));
            if (found.getDataType() != Void.class) {
                this.plugin.getSLF4JLogger().warn("staging.particle {} needs extra data: no ring drawn", name);
                return null;
            }
            this.particle = found;
            return found;
        } catch (IllegalArgumentException e) {
            this.plugin.getSLF4JLogger().warn("staging.particle {} is not a particle: no ring drawn", name);
            return null;
        }
    }

    /**
     * The point moved onto the ground below or above it, within {@link #GROUND_SEARCH} blocks; left where it is
     * without one.
     */
    private static Location ground(Location point) {
        World world = point.getWorld();
        int x = point.getBlockX();
        int z = point.getBlockZ();
        int y = point.getBlockY();
        for (int feet = y + GROUND_SEARCH; feet >= y - GROUND_SEARCH; feet--) {
            if (feet <= world.getMinHeight() || feet >= world.getMaxHeight())
                continue;
            Block block = world.getBlockAt(x, feet, z);
            if (block.isPassable() && !block.getRelative(0, -1, 0).isPassable()) {
                point.setY(feet);
                return point;
            }
        }
        return point;
    }

    private static double flatDistanceSquared(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
