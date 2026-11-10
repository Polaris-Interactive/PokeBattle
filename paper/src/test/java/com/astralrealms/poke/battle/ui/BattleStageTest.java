package com.astralrealms.poke.battle.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.Map;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;

class BattleStageTest {

    private static final PokeBattleConfiguration.Stage SPOTS = new PokeBattleConfiguration.Stage(
            new PokeBattleConfiguration.Point(2, -1, 0), new PokeBattleConfiguration.Point(8, 1, 0));
    private static final PokeBattleConfiguration.Camera CAMERA = new PokeBattleConfiguration.Camera(true, 1,
            Duration.ofSeconds(6), Map.of(), 1.5, 4, 0);

    private static PokeBattleConfiguration.Shot shot(double forward, double right, double up, String lookAt) {
        return new PokeBattleConfiguration.Shot(new PokeBattleConfiguration.Point(forward, right, up), lookAt, 10, 10);
    }

    @Test
    void forwardIsTheWayThePlayerLookedAndRightIsTheirRight() {
        // Yaw 0 looks south (+Z): right is west (-X)
        BattleStage south = new BattleStage(new Location(null, 10, 64, 10, 0, 30), SPOTS, CAMERA);
        Location point = south.at(new PokeBattleConfiguration.Point(3, 2, 1));
        assertThat(point.getX()).isCloseTo(8, within(1.0E-9));
        assertThat(point.getY()).isCloseTo(65, within(1.0E-9));
        assertThat(point.getZ()).isCloseTo(13, within(1.0E-9));

        // Yaw 90 looks west (-X): right is north (-Z)
        BattleStage west = new BattleStage(new Location(null, 0, 0, 0, 90, 0), SPOTS, CAMERA);
        Location turned = west.at(new PokeBattleConfiguration.Point(3, 2, 0));
        assertThat(turned.getX()).isCloseTo(-3, within(1.0E-9));
        assertThat(turned.getZ()).isCloseTo(-2, within(1.0E-9));
    }

    @Test
    void targetsAreTheCreaturesAtLookHeightOrBetweenThem() {
        BattleStage stage = new BattleStage(new Location(null, 0, 0, 0, 0, 0), SPOTS, CAMERA);
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
        BattleStage stage = new BattleStage(new Location(null, 0, 0, 0, 0, 0), SPOTS, CAMERA);
        // Straight behind the foe, level with its look height: looking south, flat
        Location eye = stage.eye(shot(0, 1, 1, "foe"));
        assertThat(eye.getYaw()).isCloseTo(0, within(1.0E-3f));
        assertThat(eye.getPitch()).isCloseTo(0, within(1.0E-3f));

        // Above the ally, looking down at it
        Location down = stage.eye(shot(2, -1, 5, "ally"));
        assertThat(down.getPitch()).isCloseTo(90, within(1.0E-3f));
    }

    @Test
    void aBigCreatureIsFramedFromFurtherBackAndLookedAtHigher() {
        BattleStage stage = new BattleStage(new Location(null, 0, 0, 0, 0, 0), SPOTS, CAMERA);
        Location before = stage.eye(shot(0, 0, 3, "foe"));

        // 3 blocks tall, twice the reference size: twice as far, looking at twice the look height
        stage.body("foe", new BattleStage.Body(3, 2));
        Location bigTarget = stage.target("foe");
        assertThat(bigTarget.getY()).isCloseTo(2, within(1.0E-9));
        Location after = stage.eye(shot(0, 0, 3, "foe"));
        assertThat(after.toVector().distance(bigTarget.toVector()))
                .isCloseTo(2 * before.toVector().distance(bigTarget.toVector()), within(1.0E-6));

        // Gone: back to the shot as set
        stage.body("foe", null);
        assertThat(stage.eye(shot(0, 0, 3, "foe")).toVector()).isEqualTo(before.toVector());
    }

    @Test
    void anEyeSwingsOutOfABigCreaturesShadow() {
        BattleStage stage = new BattleStage(new Location(null, 0, 0, 0, 0, 0), SPOTS, CAMERA);
        // Behind the ally, looking at the middle: a 4-block ally stands across the line
        stage.body("ally", new BattleStage.Body(4, 2));
        Location eye = stage.eye(shot(0, -1, 1, "center"));

        // Never under the ally's nametag (4 blocks up), and it still sees past the ally rather than through it
        assertThat(eye.getY()).isGreaterThanOrEqualTo(4 + 0.5 - 1.0E-6);
        assertThat(eye.getX()).isNotCloseTo(1, within(0.1));
    }

    @Test
    void aShotBesideABigCreatureStaysAboveItsNametag() {
        BattleStage stage = new BattleStage(new Location(null, 0, 0, 0, 0, 0), SPOTS, CAMERA);
        // The ally-move shot: beside and behind a big ally whose nametag is 4.4 blocks up, looking across at the foe
        stage.body("ally", new BattleStage.Body(4.4, 2.9));
        Location eye = stage.eye(shot(0.5, -2.2, 1.8, "foe"));
        assertThat(eye.getY()).isGreaterThanOrEqualTo(4.4 + 0.5 - 1.0E-6);
    }

    @Test
    void theCreaturesStandOnTheirSpotsFacingEachOther() {
        BattleStage stage = new BattleStage(new Location(null, 0, 0, 0, 0, 0), SPOTS, null);
        Location ally = stage.ally();
        Location foe = stage.foe();
        assertThat(ally.getX()).isCloseTo(1, within(1.0E-9));
        assertThat(ally.getZ()).isCloseTo(2, within(1.0E-9));
        assertThat(foe.getX()).isCloseTo(-1, within(1.0E-9));
        assertThat(foe.getZ()).isCloseTo(8, within(1.0E-9));
        // Each looks along the line to the other, flat
        assertThat(ally.getDirection().getX()).isCloseTo(-2 / Math.sqrt(40), within(1.0E-6));
        assertThat(ally.getDirection().getZ()).isCloseTo(6 / Math.sqrt(40), within(1.0E-6));
        assertThat(foe.getDirection().getZ()).isCloseTo(-6 / Math.sqrt(40), within(1.0E-6));
        assertThat(ally.getPitch()).isZero();
    }
}
