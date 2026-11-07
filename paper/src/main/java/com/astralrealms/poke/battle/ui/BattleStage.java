package com.astralrealms.poke.battle.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;

/**
 * Where a battle takes place: a frame laid in front of where it started ({@code forward} the way the player looked,
 * {@code right}, {@code up}), with the two creatures' spots in it. The creatures stand there, and camera shots turn
 * into eye locations from it. In a world, the creatures stand on the ground and shots keep blocks out of the way.
 * <p>
 * The creatures standing there ({@link #body}) count too: a shot of a big creature stands further back and looks
 * higher, and an eye with a creature between it and what it looks at rises over it.
 * <p>
 * Staged battles (arenas) will give their own origin; the spots and shots stay the same.
 *
 * @param origin feet of the frame; only its yaw counts
 * @param camera null without a camera: shots then look at the creatures' feet
 * @param bodies how big the creatures standing there are, as they come and go
 */
public record BattleStage(Location origin, PokeBattleConfiguration.Stage spots,
                          PokeBattleConfiguration.@Nullable Camera camera, Bodies bodies) {

    /** Blocks a creature's spot or a shot's eye may move up (or a spot down) to find room. */
    private static final int MAX_LIFT = 4;
    /** Room kept between a pulled-in eye and the block in front of it. */
    private static final double EYE_ROOM = 0.4;
    /** How far apart the heights an eye tries are, when it rises over something. */
    private static final double LIFT_STEP = 0.5;
    /** Defaults for a camera that sets no {@code reference-size} or {@code max-scale}. */
    private static final double REFERENCE_SIZE = 1.5;
    private static final double MAX_SCALE = 4;

    public BattleStage {
        origin = origin.clone();
        origin.setPitch(0);
    }

    public BattleStage(Location origin, PokeBattleConfiguration.Stage spots,
                       PokeBattleConfiguration.@Nullable Camera camera) {
        this(origin, spots, camera, new Bodies());
    }

    /**
     * How much room a creature's model takes, in blocks: from its feet up, and across (its widest way).
     */
    public record Body(double height, double width) {
    }

    /**
     * The creatures standing on the stage, by side. Set on the main thread, read by the camera.
     */
    public static final class Bodies {

        private volatile @Nullable Body ally;
        private volatile @Nullable Body foe;
    }

    /**
     * How big the creature standing on a side ({@code ally} or {@code foe}) is, null when unknown or empty.
     */
    public @Nullable Body body(String side) {
        return side.equals("ally") ? this.bodies.ally : this.bodies.foe;
    }

    /**
     * The creature now standing on a side ({@code ally} or {@code foe}), or null when the side is empty.
     */
    public void body(String side, @Nullable Body body) {
        if (side.equals("ally"))
            this.bodies.ally = body;
        else
            this.bodies.foe = body;
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
        Location ally = this.spot(this.spots.ally()).add(0, height * this.scale(this.bodies.ally), 0);
        Location foe = this.spot(this.spots.foe()).add(0, height * this.scale(this.bodies.foe), 0);
        return switch (lookAt.toLowerCase(Locale.ROOT)) {
            case "ally" -> ally;
            case "foe" -> foe;
            default -> ally.add(foe).multiply(0.5);
        };
    }

    /**
     * The eye of a shot: at {@code from}, turned towards its target, and as many times further from it as the creature
     * it looks at is bigger than {@code reference-size} (both for {@code center}). When blocks or a creature stand
     * between them (a hill, a tree, a big ally), the eye rises up to {@link #MAX_LIFT} blocks over the tallest creature
     * (times the push-back) to see over them; failing that, it comes in front of the first block.
     */
    public Location eye(PokeBattleConfiguration.Shot shot) {
        String lookAt = shot.lookAt().toLowerCase(Locale.ROOT);
        Location target = this.target(lookAt);
        Location eye = this.at(shot.from());
        double scale = switch (lookAt) {
            case "ally" -> this.scale(this.bodies.ally);
            case "foe" -> this.scale(this.bodies.foe);
            default -> Math.max(this.scale(this.bodies.ally), this.scale(this.bodies.foe));
        };
        if (scale > 1) {
            // Further back along the same line, never lower than the shot was set (looking up from the ground)
            double height = eye.getY();
            eye = target.clone().add(eye.toVector().subtract(target.toVector()).multiply(scale));
            eye.setY(Math.max(eye.getY(), height));
        }

        World world = eye.getWorld();
        List<BoundingBox> creatures = this.obstacles(lookAt, target);
        if (!this.clear(world, creatures, target, eye)) {
            double tallest = creatures.stream().mapToDouble(BoundingBox::getHeight).max().orElse(0);
            Location free = null;
            // Pushed back, the eye has to climb as much more to see over the same creature
            double limit = (MAX_LIFT + tallest) * scale;
            for (double lift = LIFT_STEP; lift <= limit + 1.0E-6 && free == null; lift += LIFT_STEP) {
                Location raised = eye.clone().add(0, lift, 0);
                if (this.clear(world, creatures, target, raised))
                    free = raised;
            }
            if (free != null)
                eye = free;
            else if (world != null)
                eye = inFront(world, target, eye);
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

    /**
     * How many times bigger than {@code reference-size} a creature is (1 for none, or a small one), up to
     * {@code max-scale}.
     */
    private double scale(@Nullable Body body) {
        if (body == null)
            return 1;
        double reference = this.camera != null && this.camera.referenceSize() > 0 ? this.camera.referenceSize()
                : REFERENCE_SIZE;
        double max = this.camera != null && this.camera.maxScale() > 0 ? this.camera.maxScale() : MAX_SCALE;
        return Math.clamp(Math.max(body.height(), body.width()) / reference, 1, Math.max(1, max));
    }

    /**
     * The creatures a shot must see past: not the one it looks at, nor one its target stands in.
     */
    private List<BoundingBox> obstacles(String lookAt, Location target) {
        List<BoundingBox> boxes = new ArrayList<>(2);
        if (!lookAt.equals("ally"))
            this.addBox(boxes, this.bodies.ally, this.spots.ally(), target);
        if (!lookAt.equals("foe"))
            this.addBox(boxes, this.bodies.foe, this.spots.foe(), target);
        return boxes;
    }

    private void addBox(List<BoundingBox> boxes, @Nullable Body body, PokeBattleConfiguration.Point point,
                        Location target) {
        if (body == null || body.height() <= 0)
            return;
        Location feet = this.spot(point);
        double half = Math.max(body.width(), 0.1) / 2;
        BoundingBox box = new BoundingBox(feet.getX() - half, feet.getY(), feet.getZ() - half,
                feet.getX() + half, feet.getY() + body.height(), feet.getZ() + half);
        if (!box.contains(target.toVector()))
            boxes.add(box);
    }

    /**
     * Whether the eye sees the target past blocks (in a world) and the creatures in the way.
     */
    private boolean clear(@Nullable World world, List<BoundingBox> creatures, Location target, Location eye) {
        Vector line = eye.toVector().subtract(target.toVector());
        double distance = line.length();
        if (distance > 1.0E-3) {
            Vector direction = line.clone().normalize();
            for (BoundingBox box : creatures)
                if (box.contains(eye.toVector()) || box.rayTrace(target.toVector(), direction, distance) != null)
                    return false;
        }
        return world == null || clear(world, target, eye);
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
