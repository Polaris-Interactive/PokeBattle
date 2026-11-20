package com.astralrealms.poke.battle.ui.stage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MoveSkillsTest {

    private static final List<String> PATTERNS = List.of("move-{move}", "move-{type}-{category}");

    @TempDir
    Path folder;

    private MoveSkills load() throws IOException {
        Path file = this.folder.resolve("move-skills.yml");
        Files.writeString(file, """
                slam: { skill: move-fx-reach, placeholders: { reach-next: move-slam-smack } }
                furyattack: { placeholders: { wait-next: move-furyattack-jab-2, wait-range: 0.006 } }
                blank: { skill: "" }
                """);
        return MoveSkills.load(file.toFile());
    }

    @Test
    void readsEachMovesSkillAndPlaceholders() throws IOException {
        MoveSkills skills = this.load();

        assertThat(skills.size()).isEqualTo(3);
        assertThat(skills.get("slam")).isEqualTo(new MoveSkills.Entry("move-fx-reach",
                Map.of("reach-next", "move-slam-smack")));
        assertThat(skills.get("furyattack").skill()).isNull();
        assertThat(skills.get("blank").skill()).isNull();
        assertThat(skills.get("ember")).isNull();
        assertThat(skills.get(null)).isNull();
    }

    @Test
    void mappedSkillIsTriedBeforeTheConfiguredOnes() throws IOException {
        MoveSkills skills = this.load();

        assertThat(skills.candidates("slam", PATTERNS, "normal", "physical"))
                .containsExactly("move-fx-reach", "move-slam", "move-normal-physical");
        assertThat(skills.candidates("furyattack", PATTERNS, "normal", "physical"))
                .containsExactly("move-furyattack", "move-normal-physical");
        assertThat(skills.candidates("ember", PATTERNS, "fire", "special"))
                .containsExactly("move-ember", "move-fire-special");
    }

    @Test
    void typeAndCategoryPatternsAreSkippedForAnUnknownMove() throws IOException {
        assertThat(this.load().candidates("mystery", PATTERNS, null, null)).containsExactly("move-mystery");
    }

    @Test
    void placeholdersAreTheMoveIdAndItsMappedOnesWithNumbersAsFloats() throws IOException {
        MoveSkills skills = this.load();

        assertThat(skills.placeholders("furyattack"))
                .containsEntry("move-id", "furyattack")
                .containsEntry("move-wait-next", "move-furyattack-jab-2")
                .containsEntry("move-wait-range", 0.006F)
                .hasSize(3);
        assertThat(skills.placeholders("ember")).containsOnlyKeys("move-id");
    }

    @Test
    void noFileMeansNoMapping() {
        MoveSkills skills = MoveSkills.load(new File(this.folder.toFile(), "missing.yml"));

        assertThat(skills.size()).isZero();
        assertThat(skills.candidates("slam", PATTERNS, "normal", "physical"))
                .containsExactly("move-slam", "move-normal-physical");
    }
}
