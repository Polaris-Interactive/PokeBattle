package com.astralrealms.poke.battle.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ConfigurationDefaultsTest {

    @Test
    void sectionsMissingFromAnOlderConfigReadAsTheirDefaults() {
        // What Configurate builds when the sections are absent: records with null fields, or no record at all
        PokeBattleConfiguration empty = new PokeBattleConfiguration(null, null, null,
                new PokeBattleConfiguration.Recovery(null), new PokeBattleConfiguration.Arena(null, null), null, null,
                null, null, null);
        PokeBattleConfiguration absent = new PokeBattleConfiguration(null, null, null, null, null, null, null, null,
                null, null);

        for (PokeBattleConfiguration configuration : new PokeBattleConfiguration[]{empty, absent}) {
            assertThat(configuration.recoveryOrDefault().checkInterval()).isEqualTo(Duration.ofSeconds(2));
            assertThat(configuration.arenaOrDefault().stageSpawn()).isEqualTo("stage");
            assertThat(configuration.arenaOrDefault().returnDelay()).isEqualTo(Duration.ofSeconds(3));
        }
    }

    @Test
    void aPartialSectionKeepsWhatItSets() {
        PokeBattleConfiguration partial = new PokeBattleConfiguration(null, null, null, null,
                new PokeBattleConfiguration.Arena("podium", null), null, null, null, null, null);

        assertThat(partial.arenaOrDefault().stageSpawn()).isEqualTo("podium");
        assertThat(partial.arenaOrDefault().returnDelay()).isEqualTo(Duration.ofSeconds(3));
    }
}
