package com.astralrealms.poke.battle.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import com.astralrealms.poke.battle.api.model.StageSpots;
import com.astralrealms.poke.core.mechanics.generation.GeneratedCreature;
import com.astralrealms.poke.core.mechanics.generation.SignedRoll;
import com.astralrealms.poke.core.model.Gender;
import com.astralrealms.poke.core.model.stat.StatSpread;
import com.astralrealms.poke.core.team.TrainerMember;

class BattleContextTest {

    @Test
    void survivesRedisAsJson() {
        GeneratedCreature creature = new GeneratedCreature("fakemon", null, 7, new StatSpread(1, 2, 3, 4, 5, 6),
                "calm", "ability-a", true, Gender.FEMALE, false, List.of("move-a", "move-b"), 70);
        SignedRoll roll = new SignedRoll(UUID.randomUUID(), 1234L, creature, "c2lnbmF0dXJl");
        BattleContext context = new BattleContext(UUID.randomUUID(), "polariswild", UUID.randomUUID(),
                List.of(UUID.randomUUID(), UUID.randomUUID()), List.of(Battle.Foe.of("1", creature)),
                new Battle.Options(true, true, false, roll, Set.of("night", "water"), "world"));

        BattleContext read = BattleContext.fromJson(context.toJson());

        assertThat(read).isEqualTo(context);
        assertThat(read.options().wildRoll()).isEqualTo(roll);
        assertThat(read.options().experience()).isEqualTo(1.0);
        assertThat(read.options().tag()).isNull();
    }

    @Test
    void trainerBattleKeepsItsTeamNameMultiplierAndTag() {
        TrainerMember lead = new TrainerMember("fakemon", "alt", 12, List.of("move-a"), null, "item-a", null, null,
                null, null, false, "Rocky");
        BattleContext context = new BattleContext(UUID.randomUUID(), "polarisgym", UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                List.of(Battle.Foe.of("1", lead), Battle.Foe.of("2", TrainerMember.of("othermon", null, 14))),
                new Battle.Options(false, false, false, null, Set.of(), "gym", "Pierre", 1.5,
                        "poketrainers:pierre"));

        BattleContext read = BattleContext.fromJson(context.toJson());

        assertThat(read).isEqualTo(context);
        assertThat(read.foes().getFirst()).isEqualTo(new Battle.Foe("1", "fakemon", "alt", 12, "Rocky"));
        assertThat(read.options().trainer()).isEqualTo("Pierre");
        assertThat(read.options().experience()).isEqualTo(1.5);
        assertThat(read.options().tag()).isEqualTo("poketrainers:pierre");
    }

    @Test
    void contextWrittenBeforeTrainersStillReads() {
        // Foes were whole wild creatures, with no label; options had no trainer, multiplier nor tag
        String json = """
                {"battleId":"0190f1c4-0000-7000-8000-000000000001","format":"polariswild",
                 "playerId":"0190f1c4-0000-7000-8000-000000000002","allies":[],
                 "foes":[{"species":"fakemon","level":7,"ivs":{"hp":1,"atk":2,"def":3,"spa":4,"spd":5,"spe":6},
                          "nature":"calm","ability":"ability-a","hiddenAbility":true,"gender":"F","shiny":false,
                          "moves":["move-a"],"friendship":70}],
                 "options":{"capture":true,"bag":true,"flee":true,"captureFlags":[],"metAt":"world"}}""";

        BattleContext read = BattleContext.fromJson(json);

        Battle.Foe foe = read.foes().getFirst();
        assertThat(foe.species()).isEqualTo("fakemon");
        assertThat(foe.level()).isEqualTo(7);
        assertThat(foe.labelled("1").label()).isEqualTo("1");
        assertThat(read.options().experience()).isEqualTo(1.0);
        assertThat(read.options().trainer()).isNull();
        assertThat(read.options().tag()).isNull();
    }

    @Test
    void gymStagingSurvivesRedisAsJson() {
        Location stage = new Location(null, 11.5, 95, -29.5, 180, 0);
        StageSpots spots = new StageSpots(new Location(null, 11.5, 95, -32.5),
                new Location(null, 10.5, 95, -45.5, 90, 5));
        BattleContext context = new BattleContext(UUID.randomUUID(), "polarisgym", UUID.randomUUID(),
                List.of(UUID.randomUUID()), List.of(Battle.Foe.of("1", TrainerMember.of("fakemon", null, 14))),
                new Battle.Options(false, true, false, null, Set.of(), "arena-rock", "Pierre", null,
                        "poketrainers:pierre"),
                BattleContext.Staging.of("instance-7", stage, spots));

        BattleContext read = BattleContext.fromJson(context.toJson());

        assertThat(read).isEqualTo(context);
        BattleContext.Staging staging = read.staging();
        assertThat(staging).isNotNull();
        assertThat(staging.world()).isEqualTo("instance-7");
        assertThat(staging.stage(null)).isEqualTo(stage);
        StageSpots back = staging.spots(null);
        assertThat(back).isNotNull();
        assertThat(back.ally()).isEqualTo(spots.ally());
        assertThat(back.foe()).isEqualTo(spots.foe());
    }

    @Test
    void stagingWithoutCreatureSpotsKeepsTheConfiguredOffsets() {
        // A wild arena: staged at the template's stage spawn, creatures at the configured offsets
        BattleContext.Staging staging = BattleContext.Staging.of("arena-1", new Location(null, 0.5, 64, 0.5, 90, 0),
                null);
        BattleContext context = new BattleContext(UUID.randomUUID(), "polariswild", UUID.randomUUID(), List.of(),
                List.of(), new Battle.Options(true, true, true, null, Set.of(), "arena"), staging);

        BattleContext read = BattleContext.fromJson(context.toJson());

        assertThat(read.staging()).isEqualTo(staging);
        assertThat(read.staging().spots(null)).isNull();
        assertThat(read.staging().stage(null).getYaw()).isEqualTo(90);
    }

    @Test
    void unstagedBattleWritesNoStaging() {
        BattleContext context = new BattleContext(UUID.randomUUID(), "polariswild", UUID.randomUUID(), List.of(),
                List.of(), new Battle.Options(true, true, true, null, Set.of(), "world"));

        String json = context.toJson();

        assertThat(json).doesNotContain("staging");
        assertThat(BattleContext.fromJson(json).staging()).isNull();
    }

    @Test
    void contextWrittenBeforeStagingStillReads() {
        // A gym battle taken over from a shard that did not keep where it was staged
        String json = """
                {"battleId":"0190f1c4-0000-7000-8000-000000000001","format":"polarisgym",
                 "playerId":"0190f1c4-0000-7000-8000-000000000002","allies":[],
                 "foes":[{"label":"1","species":"fakemon","level":14}],
                 "options":{"capture":false,"bag":true,"flee":false,"captureFlags":[],"metAt":"arena-rock",
                            "trainer":"Pierre","tag":"poketrainers:pierre"}}""";

        BattleContext read = BattleContext.fromJson(json);

        assertThat(read.staging()).isNull();
        assertThat(read.options().tag()).isEqualTo("poketrainers:pierre");
        assertThat(read.foes().getFirst().level()).isEqualTo(14);
    }
}
