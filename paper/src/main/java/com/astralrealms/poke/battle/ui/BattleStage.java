package com.astralrealms.poke.battle.ui;

import java.util.Locale;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;

/**
 * Where a battle takes place: a frame laid in front of where it started ({@code forward} the way the player looked,
 * {@code right}, {@code up}), with the two creatures' spots in it. The creatures stand there, and camera shots turn
 * into eye locations from it. In a world, the creatures stand on the ground and shots keep blocks out of the way.
 * <p>
 * Staged battles (arenas) will give their own origin; the spots and shots stay the same.
 *
 * @param origin feet of the frame; only its yaw counts
 * @param camera null without a camera: shots then look at the creatures' feet
 */
public record BattleStage(Location origin, PokeBattleConfiguration.Stage spots,
                          PokeBattleConfiguration.@Nullable Camera camera) {

    /** Blocks a creature's spot or a shot's eye may move up (or a spot down) to find room. */
    private static final int MAX_LIFT = 4;
    /** Room kept between a pulled-in eye and the block in front of it. */
    private static final double EYE_ROOM = 0.4;

    public BattleStage {
        origin = origin.clone();
        origin.setPitch(0);
    }

    /**
     * A point of the frame, in the world.
     */
    public Location at(PokeBattleConfiguration.Point point) {
        double yaw = Math.toRadians(this.origin.getYaw());
        Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        return this.origin.clone()
                .add(forward.multiply(point.forward()))
                .add(right.multiply(point.right()))
                .add(0, point.up(), 0);
    }

    /**
     * Where the player's creature stands, facing the opponent.
     */
    public Location ally() {
        return facing(this.spot(this.spots.ally()), this.spot(this.spots.foe()));
    }

    /**
     * Where the opponent stands, facing the player's creature.
     */
    public Location foe() {
        return facing(this.spot(this.spots.foe()), this.spot(this.spots.ally()));
    }

    /**
     * What a shot looks at: a creature at {@code look-height}, or the middle of the two. Unknown names look at the
     * middle.
     */
    public Location target(String lookAt) {
        double height = this.camera == null ? 0 : this.camera.lookHeight();
        Location ally = this.spot(this.spots.ally()).add(0, height, 0);
        Location foe = this.spot(this.spots.foe()).add(0, height, 0);
        return switch (lookAt.toLowerCase(Locale.ROOT)) {
            case "ally" -> ally;
            case "foe" -> foe;
            default -> ally.add(foe).multiply(0.5);
        };
    }

    /**
     * The eye of a shot: at {@code from}, turned towards its target. When blocks stand between them (a hill, a tree,
     * a wall), the eye rises up to {@link #MAX_LIFT} blocks to see over them, else comes in front of the first one.
     */
    public Location eye(PokeBattleConfiguration.Shot shot) {
        Location target = this.target(shot.lookAt());
        Location eye = this.at(shot.from());
        World world = eye.getWorld();
        if (world != null && !clear(world, target, eye)) {
            Location free = null;
            for (int lift = 1; lift <= MAX_LIFT && free == null; lift++) {
                Location raised = eye.clone().add(0, lift, 0);
                if (clear(world, target, raised))
                    free = raised;
            }
            eye = free != null ? free : inFront(world, target, eye);
        }
        Vector direction = target.toVector().subtract(eye.toVector());
        if (direction.lengthSquared() > 1.0E-6)
            eye.setDirection(direction);
        return eye;
    }

    /**
     * A creature's spot, on the ground: the nearest place within {@link #MAX_LIFT} blocks up or down where it stands
     * on something with room above it. Left where it is when there is none (or no world, in tests).
     */
    private Location spot(PokeBattleConfiguration.Point point) {
        Location spot = this.at(point);
        World world = spot.getWorld();
        if (world == null)
            return spot;
        int x = spot.getBlockX();
        int y = spot.getBlockY();
        int z = spot.getBlockZ();
        for (int i = 0; i <= MAX_LIFT * 2; i++) {
            // 0, +1, -1, +2, -2...
            int dy = (i + 1) / 2 * (i % 2 == 1 ? 1 : -1);
            int feet = y + dy;
            if (feet <= world.getMinHeight() || feet + 1 >= world.getMaxHeight())
                continue;
            Block block = world.getBlockAt(x, feet, z);
            if (block.isPassable() && block.getRelative(BlockFace.UP).isPassable()
                    && !block.getRelative(BlockFace.DOWN).isPassable()) {
                spot.setY(feet);
                return spot;
            }
        }
        return spot;
    }

    private static boolean clear(World world, Location from, Location to) {
        Vector line = to.toVector().subtract(from.toVector());
        double distance = line.length();
        if (distance < 1.0E-3)
            return world.getBlockAt(to).isPassable();
        // Grass and flowers do not block the view
        RayTraceResult hit = world.rayTraceBlocks(from, line.normalize(), distance + EYE_ROOM,
                FluidCollisionMode.NEVER, true);
        return hit == null;
    }

    /**
     * On the line from the target to the eye, just in front of the first block in the way.
     */
    private static Location inFront(World world, Location target, Location eye) {
        Vector line = eye.toVector().subtract(target.toVector());
        double distance = line.length();
        if (distance < 1.0E-3)
            return eye;
        Vector direction = line.normalize();
        RayTraceResult hit = world.rayTraceBlocks(target, direction, distance + EYE_ROOM, FluidCollisionMode.NEVER, true);
        if (hit == null)
            return eye;
        double reach = Math.max(0, hit.getHitPosition().distance(target.toVector()) - EYE_ROOM);
        Location closer = target.clone().add(direction.multiply(reach));
        closer.setYaw(eye.getYaw());
        closer.setPitch(eye.getPitch());
        return closer;
    }

    private static Location facing(Location from, Location towards) {
        Vector direction = towards.toVector().subtract(from.toVector()).setY(0);
        if (direction.lengthSquared() > 1.0E-6)
            from.setDirection(direction);
        return from;
    }
}
