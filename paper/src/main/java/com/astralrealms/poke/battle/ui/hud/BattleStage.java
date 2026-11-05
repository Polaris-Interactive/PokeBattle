package com.astralrealms.poke.battle.ui.hud;

import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;

/**
 * Where a battle takes place: a frame laid in front of where it started ({@code forward} the way the player looked,
 * {@code right}, {@code up}), with the two creatures' spots in it. Turns camera shots into eye locations.
 * <p>
 * Staged battles (arenas) will give their own origin; the shots stay the same.
 *
 * @param origin feet of the frame; only its yaw counts
 */
record BattleStage(Location origin, PokeBattleConfiguration.Camera camera) {

    BattleStage {
        origin = origin.clone();
        origin.setPitch(0);
    }

    /**
     * A point of the frame, in the world.
     */
    Location at(PokeBattleConfiguration.Point point) {
        double yaw = Math.toRadians(this.origin.getYaw());
        Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        return this.origin.clone()
                .add(forward.multiply(point.forward()))
                .add(right.multiply(point.right()))
                .add(0, point.up(), 0);
    }

    /**
     * What a shot looks at: a creature at {@code look-height}, or the middle of the two. Unknown names look at the
     * middle.
     */
    Location target(String lookAt) {
        Location ally = this.at(this.camera.ally()).add(0, this.camera.lookHeight(), 0);
        Location foe = this.at(this.camera.foe()).add(0, this.camera.lookHeight(), 0);
        return switch (lookAt.toLowerCase(Locale.ROOT)) {
            case "ally" -> ally;
            case "foe" -> foe;
            default -> ally.add(foe).multiply(0.5);
        };
    }

    /**
     * The eye of a shot: at {@code from}, turned towards its target.
     */
    Location eye(PokeBattleConfiguration.Shot shot) {
        Location eye = this.at(shot.from());
        Vector direction = this.target(shot.lookAt()).toVector().subtract(eye.toVector());
        if (direction.lengthSquared() > 1.0E-6)
            eye.setDirection(direction);
        return eye;
    }
}
