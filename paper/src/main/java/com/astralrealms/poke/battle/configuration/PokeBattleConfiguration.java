package com.astralrealms.poke.battle.configuration;

import java.time.Duration;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import com.astralrealms.poke.battle.model.RewardCalculator;

/**
 * {@code config.yml}. Values the GDD leaves open live here with a documented default rather than in code.
 */
@ConfigSerializable
public record PokeBattleConfiguration(Settings settings, Start start, Battle battle, Rewards rewards, Dialog dialog) {

    @ConfigSerializable
    public record Settings(boolean debug) {
    }

    /**
     * @param timeout  how long a worker has to take a battle
     * @param attempts tries with the same battle id when none answers
     */
    @ConfigSerializable
    public record Start(Duration timeout, int attempts) {
    }

    /**
     * @param turnTimerSeconds 0 for no timer
     * @param lockTtl          per-player lock, refreshed on every request
     * @param disconnectGrace  time a player may be away before the battle is lost
     * @param endTimeout       time a worker has to confirm a stop before the players are released anyway
     */
    @ConfigSerializable
    public record Battle(int turnTimerSeconds, Duration lockTtl, Duration disconnectGrace, Duration endTimeout) {
    }

    /**
     * @param experienceOnCapture a catch rewards like a win (modern rule)
     */
    @ConfigSerializable
    public record Rewards(double experienceMultiplier, boolean effortValues, int friendshipOnWin,
                          boolean experienceOnCapture) {

        public RewardCalculator.Rules rules() {
            return new RewardCalculator.Rules(this.experienceMultiplier, this.effortValues, this.friendshipOnWin);
        }
    }

    @ConfigSerializable
    public record Dialog(int moveColumns) {
    }
}
