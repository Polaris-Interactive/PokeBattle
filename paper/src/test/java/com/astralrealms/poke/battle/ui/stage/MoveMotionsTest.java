package com.astralrealms.poke.battle.ui.stage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

class MoveMotionsTest {

    @TempDir
    Path folder;

    @Test
    void readsEachMovesMotionAndSkipsStayAndUnknownOnes() throws IOException {
        Path file = this.folder.resolve("motions.yml");
        Files.writeString(file, """
                ram: { motion: dash }
                pounce: { motion: leap, height: 2.5, speed: 12 }
                poke: { motion: lunge, reach: 1 }
                gaze: { motion: stay }
                wobble: { motion: wiggle }
                """);
        MoveMotions motions = MoveMotions.load(file.toFile(), NOPLogger.NOP_LOGGER);

        assertThat(motions.size()).isEqualTo(3);
        assertThat(motions.get("ram")).isEqualTo(new MoveMotions.Motion(MoveMotions.Kind.DASH, 0, 0, 0));
        assertThat(motions.get("pounce")).isEqualTo(new MoveMotions.Motion(MoveMotions.Kind.LEAP, 12, 2.5, 0));
        assertThat(motions.get("poke").reach()).isEqualTo(1);
        assertThat(motions.get("gaze")).isNull();
        assertThat(motions.get("wobble")).isNull();
        assertThat(motions.get(null)).isNull();
    }

    @Test
    void noFileMeansNoMotion() {
        MoveMotions motions = MoveMotions.load(new File(this.folder.toFile(), "missing.yml"), NOPLogger.NOP_LOGGER);
        assertThat(motions.size()).isZero();
    }
}
