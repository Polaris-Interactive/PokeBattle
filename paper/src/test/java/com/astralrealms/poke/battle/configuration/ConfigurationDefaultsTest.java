package com.astralrealms.poke.battle.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ConfigurationDefaultsTest {

    @Test
    void sectionsMissingFromAnOlderConfigGetTheirDefaults() {
        // What Configurate builds when the section is absent: every field null
        assertThat(new PokeBattleConfiguration.Recovery(null).checkInterval()).isEqualTo(Duration.ofSeconds(2));
        PokeBattleConfiguration.Arena arena = new PokeBattleConfiguration.Arena(null, null);
        assertThat(arena.stageSpawn()).isEqualTo("stage");
        assertThat(arena.returnDelay()).isEqualTo(Duration.ofSeconds(3));
    }
}
