package com.astralrealms.poke.battle.ui.stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.LongSupplier;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * One creature moving for its move ({@link MoveMotions.Motion}): it goes from its spot ({@code home}) to the target
 * ({@code contact}, just in front of it), strikes there, waits for its strike to land and goes back. The creature
 * keeps facing the way it faced at home the whole time, so it backs off to its spot facing the opponent.
 * <p>
 * Moved by teleporting it every tick on its own scheduler, with gravity off while it moves. {@link #cancel()} puts it
 * back on its spot at once.
 */
final class StageMotion {

    /** Default speeds, blocks per second: towards the target, and back. */
    private static final double DASH_SPEED = 16;
    private static final double LEAP_SPEED = 11;
    private static final double LUNGE_SPEED = 9;
    private static final double DIVE_SPEED = 20;
    private static final double BACK_SPEED = 9;
    /** Default heights and reach, blocks. */
    private static final double LEAP_HEIGHT = 2;
    private static final double HOP_HEIGHT = 1;
    private static final double SOAR_HEIGHT = 5;
    private static final double LUNGE_REACH = 1.5;
    /** How deep a burrowing creature goes, below its own height. */
    private static final double BURROW_EXTRA = 0.5;
    private static final int BURROW_TICKS = 6;
    private static final int RISE_TICKS = 8;
    private static final int HOP_TICKS = 9;
    private static final int MIN_TICKS = 3;

    private record Segment(int ticks, DoubleFunction<Location> at) {
    }

    private final Plugin plugin;
    private final Entity entity;
    private final Location home;
    private final List<Segment> out;
    private final List<Segment> back;
    private final LongSupplier strike;

    private @Nullable ScheduledTask task;
    private boolean gravity;
    private boolean done;

    /**
     * @param height the creature's height in blocks (how deep it burrows), 0 when unknown
     * @param strike plays the strike (animation, effect) and says how many ticks to wait before going back
     */
    StageMotion(Plugin plugin, Entity entity, Location home, Location contact, MoveMotions.Motion motion,
                double height, LongSupplier strike) {
        this.plugin = plugin;
        this.entity = entity;
        this.home = home.clone();
        this.strike = strike;
        this.out = new ArrayList<>();
        this.back = new ArrayList<>();
        plan(motion, this.home, contact, height, this.out, this.back);
    }

    /**
     * How long the creature takes to reach its strike, in ticks: the beat waits that much more before the hit shows.
     */
    static long ticksToStrike(MoveMotions.Motion motion, Location home, Location contact, double height) {
        List<Segment> out = new ArrayList<>();
        plan(motion, home.clone(), contact, height, out, new ArrayList<>());
        return out.stream().mapToLong(Segment::ticks).sum();
    }

    void start() {
        this.gravity = this.entity.hasGravity();
        this.entity.setGravity(false);
        this.task = this.entity.getScheduler().runAtFixedRate(this.plugin, new Runner(), this::finish, 1L, 1L);
        if (this.task == null)
            this.finish();
    }

    /**
     * Stops it where it is and puts the creature back on its spot.
     */
    void cancel() {
        if (this.task != null)
            this.task.cancel();
        this.finish();
    }

    private void finish() {
        if (this.done)
            return;
        this.done = true;
        if (this.entity.isValid()) {
            this.entity.teleport(this.home);
            this.entity.setVelocity(new Vector());
            this.entity.setGravity(this.gravity);
        }
    }

    /**
     * Steps through the way out, the strike, the wait and the way back, one tick at a time.
     */
    private final class Runner implements java.util.function.Consumer<ScheduledTask> {

        private final List<Segment> path = new ArrayList<>();
        private int segment;
        private int tick;
        private boolean struck;
        private long hold;

        Runner() {
            this.path.addAll(StageMotion.this.out);
        }

        @Override
        public void accept(ScheduledTask task) {
            if (!StageMotion.this.entity.isValid()) {
                task.cancel();
                StageMotion.this.done = true;
                return;
            }
            if (this.segment < this.path.size()) {
                Segment current = this.path.get(this.segment);
                this.tick++;
                StageMotion.this.entity.teleport(current.at().apply(Math.min(1, (double) this.tick / current.ticks())));
                if (this.tick >= current.ticks()) {
                    this.segment++;
                    this.tick = 0;
                }
                return;
            }
            if (!this.struck) {
                this.struck = true;
                this.hold = Math.max(1, StageMotion.this.strike.getAsLong());
                return;
            }
            if (this.hold-- > 0)
                return;
            if (StageMotion.this.back.isEmpty() || this.path.size() > StageMotion.this.out.size()) {
                task.cancel();
                StageMotion.this.finish();
                return;
            }
            this.path.addAll(StageMotion.this.back);
        }
    }

    // ---- Paths ----

    private static void plan(MoveMotions.Motion motion, Location home, Location contact, double height,
                             List<Segment> out, List<Segment> back) {
        Location target = contact.clone();
        target.setYaw(home.getYaw());
        target.setPitch(home.getPitch());
        switch (motion.kind()) {
            case DASH -> {
                out.add(straight(home, target, speed(motion, DASH_SPEED), 0, true));
                back.add(straight(target, home, BACK_SPEED, 0.4, false));
            }
            case LEAP -> {
                double top = motion.height() > 0 ? motion.height() : LEAP_HEIGHT;
                out.add(straight(home, target, speed(motion, LEAP_SPEED), top, false));
                back.add(straight(target, home, BACK_SPEED, top / 3, false));
            }
            case LUNGE -> {
                double reach = motion.reach() > 0 ? motion.reach() : LUNGE_REACH;
                Vector way = target.toVector().subtract(home.toVector()).setY(0);
                double distance = way.length();
                Location step = distance < 1.0E-3 ? home.clone()
                        : home.clone().add(way.multiply(Math.min(reach, distance) / distance));
                out.add(straight(home, step, speed(motion, LUNGE_SPEED), 0, true));
                back.add(straight(step, home, LUNGE_SPEED * 0.7, 0, false));
            }
            case HOP -> {
                double top = motion.height() > 0 ? motion.height() : HOP_HEIGHT;
                out.add(new Segment(HOP_TICKS, t -> home.clone().add(0, top * 4 * t * (1 - t), 0)));
            }
            case BURROW -> {
                double depth = Math.max(1, height) + BURROW_EXTRA;
                Location underHome = home.clone().add(0, -depth, 0);
                Location underTarget = target.clone().add(0, -depth, 0);
                out.add(straight(home, underHome, BURROW_TICKS));
                out.add(straight(underTarget, target, BURROW_TICKS));
                back.add(straight(target, underTarget, BURROW_TICKS));
                back.add(straight(underHome, home, BURROW_TICKS));
            }
            case SOAR -> {
                double top = motion.height() > 0 ? motion.height() : SOAR_HEIGHT;
                Location sky = home.clone().add(0, top, 0);
                out.add(straight(home, sky, RISE_TICKS));
                out.add(straight(sky, target, speed(motion, DIVE_SPEED), 0, true));
                back.add(straight(target, home, BACK_SPEED, 1, false));
            }
        }
    }

    private static double speed(MoveMotions.Motion motion, double fallback) {
        return motion.speed() > 0 ? motion.speed() : fallback;
    }

    /**
     * From {@code from} to {@code to} at {@code speed} blocks per second, rising {@code arc} blocks in the middle;
     * {@code accelerate} starts slow and ends fast (a charge), otherwise it eases in and out.
     */
    private static Segment straight(Location from, Location to, double speed, double arc, boolean accelerate) {
        int ticks = Math.max(MIN_TICKS, (int) Math.ceil(from.distance(to) / speed * 20));
        return new Segment(ticks, t -> {
            double s = accelerate ? t * t : t * t * (3 - 2 * t);
            return between(from, to, s).add(0, arc * 4 * t * (1 - t), 0);
        });
    }

    private static Segment straight(Location from, Location to, int ticks) {
        return new Segment(ticks, t -> between(from, to, t));
    }

    private static Location between(Location from, Location to, double s) {
        Location at = from.clone().add(to.toVector().subtract(from.toVector()).multiply(s));
        at.setYaw(from.getYaw());
        at.setPitch(from.getPitch());
        return at;
    }
}
