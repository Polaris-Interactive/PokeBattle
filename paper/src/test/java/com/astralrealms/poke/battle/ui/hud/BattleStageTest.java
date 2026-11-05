package com.astralrealms.poke.battle.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.Map;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;

class BattleStageTest {

    private static final PokeBattleConfiguration.Camera CAMERA = new PokeBattleConfiguration.Camera(true,
            new PokeBattleConfiguration.Point(2, -1, 0), new PokeBattleConfiguration.Point(8, 1, 0), 1,
            Duration.ofSeconds(6), Map.of());

    private static PokeBattleConfiguration.Shot shot(double forward, double right, double up, String lookAt) {
        return new PokeBattleConfiguration.Shot(new PokeBattleConfiguration.Point(forward, right, up), lookAt, 10, 10);
    }

    @Test
    void forwardIsTheWayThePlayerLookedAndRightIsTheirRight() {
        // Yaw 0 looks south (+Z): right is west (-X)
        BattleStage south = new BattleStage(new Location(null, 10, 64, 10, 0, 30), CAMERA);
        Location point = south.at(new PokeBattleConfiguration.Point(3, 2, 1));
        assertThat(point.getX()).isCloseTo(8, within(1.0E-9));
        assertThat(point.getY()).isCloseTo(65, within(1.0E-9));
        assertThat(point.getZ()).isCloseTo(13, within(1.0E-9));

        // Yaw 90 looks west (-X): right is north (-Z)
        BattleStage west = new BattleStage(new Location(null, 0, 0, 0, 90, 0), CAMERA);
        Location turned = west.at(new PokeBattleConfiguration.Point(3, 2, 0));
        assertThat(turned.getX()).isCloseTo(-3, within(1.0E-9));
        assertThat(turned.getZ()).isCloseTo(-2, within(1.0E-9));
    }

    @Test
    void targetsAreTheCreaturesAtLookHeightOrBetweenThem() {
        BattleStage stage = new BattleStage(new Location(null, 0, 0, 0, 0, 0), CAMERA);
        Location ally = stage.target("ally");
        assertThat(ally.getX()).isCloseTo(1, within(1.0E-9));
        assertThat(ally.getY()).isCloseTo(1, within(1.0E-9));
        assertThat(ally.getZ()).isCloseTo(2, within(1.0E-9));
        Location center = stage.target("center");
        assertThat(center.getX()).isCloseTo(0, within(1.0E-9));
        assertThat(center.getZ()).isCloseTo(5, within(1.0E-9));
        assertThat(stage.target("anything").toVector()).isEqualTo(center.toVector());
    }

    @Test
    void aShotLooksAtItsTarget() {
        BattleStage stage = new BattleStage(new Location(null, 0, 0, 0, 0, 0), CAMERA);
        // Straight behind the foe, level with its look height: looking south, flat
        Location eye = stage.eye(shot(0, 1, 1, "foe"));
        assertThat(eye.getYaw()).isCloseTo(0, within(1.0E-3f));
        assertThat(eye.getPitch()).isCloseTo(0, within(1.0E-3f));

        // Above the ally, looking down at it
        Location down = stage.eye(shot(2, -1, 5, "ally"));
        assertThat(down.getPitch()).isCloseTo(90, within(1.0E-3f));
    }
}
