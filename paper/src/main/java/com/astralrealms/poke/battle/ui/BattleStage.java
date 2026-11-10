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
 * The creatures standing there ({@link #body}) count too: a shot looks at a creature's middle whatever its size, backs
 * off only as far as it takes for the creatures it shows to fit in view, stays above a creature standing in its
 * foreground, and gets around one standing between it and what it looks at without changing sides.
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
    /** How far above a creature's nametag an eye stays, at least. */
    private static final double CLEAR_ABOVE = 0.5;
    /** How far, in degrees either way, an eye swings around its target to get clear, and the steps it tries. */
    private static final double MAX_SWING = 90;
    private static final double SWING_STEP = 5;
    /** Defaults for a camera that sets no {@code look-ratio}, {@code fit-angle} or {@code max-scale}. */
    private static final double LOOK_RATIO = 0.6;
    private static final double FIT_ANGLE = 50;
    private static final double MAX_SCALE = 4;
    /** How finely the push-back that fits a big creature is searched, as a share of the shot's distance. */
    private static final double FIT_STEP = 0.05;
    /** How much nearer to the eye than its target a creature is, in blocks, to stand in the shot's foreground. */
    private static final double FOREGROUND_MARGIN = 0.5;
    /** How far from the line between the creatures an eye is, in blocks, to be on one side of it. */
    private static final double SIDE_MARGIN = 0.5;

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
     * What a shot looks at: a creature at {@code look-ratio} of its height ({@code look-height} while its size is not
     * known), or the middle of the two. Unknown names look at the middle.
     */
    public Location target(String lookAt) {
        Location ally = this.spot(this.spots.ally()).add(0, this.lookUp(this.bodies.ally), 0);
        Location foe = this.spot(this.spots.foe()).add(0, this.lookUp(this.bodies.foe), 0);
        return switch (lookAt.toLowerCase(Locale.ROOT)) {
            case "ally" -> ally;
            case "foe" -> foe;
            default -> ally.add(foe).multiply(0.5);
        };
    }

    /**
     * The eye of a shot: at {@code from}, turned towards its target.
     * <ul>
     * <li>It backs off along its line of sight only as far as it takes for the creatures it shows (the one it looks at;
     * both for {@code center}) to fit in {@code fit-angle} of the view, at most {@code max-scale} times its distance:
     * a big creature looks big, it is not shrunk to the size the shot was laid out for.</li>
     * <li>It stays above the nametag of a creature standing in its foreground (nearer than what it looks at).</li>
     * <li>When blocks or a creature stand between it and its target (a hill, a tree, a big ally), it first swings
     * around its target to the nearest angle that sees it, without crossing the line between the creatures; failing
     * that, it rises up to {@link #MAX_LIFT} blocks over the tallest creature (times the push-back) to see over
     * them, and failing that it comes in front of the first block.</li>
     * </ul>
     */
    public Location eye(PokeBattleConfiguration.Shot shot) {
        String lookAt = shot.lookAt().toLowerCase(Locale.ROOT);
        Location target = this.target(lookAt);
        Location set = this.at(shot.from());
        double scale = this.fit(lookAt, target, set);
        Location eye = this.pushed(target, set, scale);

        // Above a creature in the foreground: its nametag must not hang in the middle of the view
        double reach = flat(eye, target);
        for (String side : new String[]{"ally", "foe"}) {
            Body body = this.body(side);
            if (body == null)
                continue;
            Location feet = this.feet(side);
            if (flat(eye, feet) < reach - FOREGROUND_MARGIN)
                eye.setY(Math.max(eye.getY(), feet.getY() + body.height() + CLEAR_ABOVE));
        }

        World world = eye.getWorld();
        List<BoundingBox> creatures = this.obstacles(lookAt, target);
        if (!this.clear(world, creatures, target, eye)) {
            Location swung = this.swing(world, creatures, target, eye);
            if (swung != null) {
                eye = swung;
            } else {
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
        }
        Vector direction = target.toVector().subtract(eye.toVector());
        if (direction.lengthSquared() > 1.0E-6)
            eye.setDirection(direction);
        return eye;
    }

    /**
     * How many times its distance a shot backs off for the creatures it shows to fit in view: 1 when they already
     * do (or none is known), up to {@code max-scale}.
     */
    private double fit(String lookAt, Location target, Location set) {
        double max = this.camera != null && this.camera.maxScale() > 0 ? this.camera.maxScale() : MAX_SCALE;
        double angle = this.camera != null && this.camera.fitAngle() > 0 ? this.camera.fitAngle() : FIT_ANGLE;
        double perSize = 1 / (2 * Math.tan(Math.toRadians(Math.min(angle, 170)) / 2));
        List<String> shown = switch (lookAt) {
            case "ally" -> List.of("ally");
            case "foe" -> List.of("foe");
            default -> List.of("ally", "foe");
        };
        // Each creature shown: its middle, and how far the eye has to be for it to fit
        List<Vector> middles = new ArrayList<>(2);
        List<Double> rooms = new ArrayList<>(2);
        for (String side : shown) {
            Body body = this.body(side);
            if (body == null)
                continue;
            middles.add(this.feet(side).add(0, body.height() / 2, 0).toVector());
            rooms.add(Math.max(body.height(), body.width()) * perSize);
        }
        for (double scale = 1; scale < max; scale += FIT_STEP) {
            Vector eye = this.pushed(target, set, scale).toVector();
            boolean fits = true;
            for (int i = 0; i < middles.size(); i++)
                fits &= eye.distance(middles.get(i)) >= rooms.get(i);
            if (fits)
                return scale;
        }
        return Math.max(1, max);
    }

    /**
     * The shot's eye {@code scale} times further from its target, along the same line, never lower than it was set
     * (looking up from the ground).
     */
    private Location pushed(Location target, Location set, double scale) {
        if (scale <= 1)
            return set.clone();
        Location eye = target.clone().add(set.toVector().subtract(target.toVector()).multiply(scale));
        eye.setY(Math.max(eye.getY(), set.getY()));
        return eye;
    }

    /**
     * Where the creature of a side ({@code ally} or {@code foe}) stands.
     */
    private Location feet(String side) {
        return this.spot(side.equals("ally") ? this.spots.ally() : this.spots.foe());
    }

    /**
     * How far above a creature's feet a shot looks at it.
     */
    private double lookUp(@Nullable Body body) {
        if (this.camera == null)
            return 0;
        if (body == null || body.height() <= 0)
            return this.camera.lookHeight();
        return body.height() * (this.camera.lookRatio() > 0 ? this.camera.lookRatio() : LOOK_RATIO);
    }

    /** The distance between two places, heights left out. */
    private static double flat(Location a, Location b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /**
     * Which side of the line between the two creatures a place is on: positive, negative, or 0 when it is on the
     * line (within {@link #SIDE_MARGIN}).
     */
    private int sideOfLine(Location place) {
        Location ally = this.feet("ally");
        Location foe = this.feet("foe");
        double dx = foe.getX() - ally.getX();
        double dz = foe.getZ() - ally.getZ();
        double length = Math.hypot(dx, dz);
        if (length < 1.0E-6)
            return 0;
        double off = (dx * (place.getZ() - ally.getZ()) - dz * (place.getX() - ally.getX())) / length;
        return Math.abs(off) < SIDE_MARGIN ? 0 : off > 0 ? 1 : -1;
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
     * The eye turned around its target, at the same distance and height, by the smallest angle (up to
     * {@link #MAX_SWING}, either way) that sees the target and keeps the eye on its side of the line between the
     * creatures. Null when no angle does.
     */
    private @Nullable Location swing(@Nullable World world, List<BoundingBox> creatures, Location target,
                                     Location eye) {
        Vector offset = eye.toVector().subtract(target.toVector());
        int side = this.sideOfLine(eye);
        for (double degrees = SWING_STEP; degrees <= MAX_SWING + 1.0E-6; degrees += SWING_STEP) {
            for (double sign : new double[]{1, -1}) {
                Location turned = target.clone().add(offset.clone().rotateAroundY(Math.toRadians(sign * degrees)));
                // The view stays on its side of the creatures: crossing their line flips left and right on screen
                if (side != 0 && this.sideOfLine(turned) == -side)
                    continue;
                if (this.clear(world, creatures, target, turned))
                    return turned;
            }
        }
        return null;
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
