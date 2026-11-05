package com.astralrealms.poke.battle.ui;

import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;

/**
 * Where a battle takes place: a frame laid in front of where it started ({@code forward} the way the player looked,
 * {@code right}, {@code up}), with the two creatures' spots in it. The creatures stand there, and camera shots turn
 * into eye locations from it.
 * <p>
 * Staged battles (arenas) will give their own origin; the spots and shots stay the same.
 *
 * @param origin feet of the frame; only its yaw counts
 * @param camera null without a camera: shots then look at the creatures' feet
 */
public record BattleStage(Location origin, PokeBattleConfiguration.Stage spots,
                          PokeBattleConfiguration.@Nullable Camera camera) {

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
        return facing(this.at(this.spots.ally()), this.at(this.spots.foe()));
    }

    /**
     * Where the opponent stands, facing the player's creature.
     */
    public Location foe() {
        return facing(this.at(this.spots.foe()), this.at(this.spots.ally()));
    }

    /**
     * What a shot looks at: a creature at {@code look-height}, or the middle of the two. Unknown names look at the
     * middle.
     */
    public Location target(String lookAt) {
        double height = this.camera == null ? 0 : this.camera.lookHeight();
        Location ally = this.at(this.spots.ally()).add(0, height, 0);
        Location foe = this.at(this.spots.foe()).add(0, height, 0);
        return switch (lookAt.toLowerCase(Locale.ROOT)) {
            case "ally" -> ally;
            case "foe" -> foe;
            default -> ally.add(foe).multiply(0.5);
        };
    }

    /**
     * The eye of a shot: at {@code from}, turned towards its target.
     */
    public Location eye(PokeBattleConfiguration.Shot shot) {
        Location eye = this.at(shot.from());
        Vector direction = this.target(shot.lookAt()).toVector().subtract(eye.toVector());
        if (direction.lengthSquared() > 1.0E-6)
            eye.setDirection(direction);
        return eye;
    }

    private static Location facing(Location from, Location towards) {
        Vector direction = towards.toVector().subtract(from.toVector()).setY(0);
        if (direction.lengthSquared() > 1.0E-6)
            from.setDirection(direction);
        return from;
    }
}
