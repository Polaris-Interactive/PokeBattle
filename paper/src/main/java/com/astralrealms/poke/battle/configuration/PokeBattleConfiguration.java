package com.astralrealms.poke.battle.configuration;

import java.time.Duration;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import com.astralrealms.poke.battle.model.RewardCalculator;

/**
 * {@code config.yml}. Values the GDD leaves open live here with a documented default rather than in code.
 */
@ConfigSerializable
public record PokeBattleConfiguration(Settings settings, Start start, Battle battle, Rewards rewards, Dialog dialog,
                                      @Nullable Hud hud) {

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

    /**
     * The battle HUD, drawn with AstralHologram in front of the player. Without AstralHologram, or disabled, the
     * choice dialog is used.
     *
     * @param closeable          whether sneaking leaves the HUD ({@code /combat} brings it back)
     * @param depth              blocks from the eye, for elements that set none
     * @param lookYaw            how far the view may turn from the centre, each way
     * @param chatLog            whether the battle log also goes to the chat
     * @param logLines           log lines shown in the HUD
     * @param closeDelay         how long the HUD stays after the battle ended
     * @param elements           where things go: {@code foe}, {@code ally}, {@code prompt}, {@code timer}, {@code log},
     *                           {@code info}, the menu buttons {@code button-1}... (as many as wanted), {@code back} and
     *                           {@code next}. An element left out is not drawn
     */
    @ConfigSerializable
    public record Hud(boolean enabled, boolean closeable, double depth, float lookYaw, float lookPitch,
                      boolean chatLog, int logLines, Duration closeDelay, String hoverBackground,
                      String disabledBackground, String hoverSound, String clickSound, Bar bar,
                      Map<String, Element> elements) {
    }

    /**
     * A health bar: {@code length} symbols, coloured by what is left.
     */
    @ConfigSerializable
    public record Bar(int length, String symbol, String high, String medium, String low, String empty) {
    }

    /**
     * One HUD element, in the format {@code /hologram hud edit} prints.
     *
     * @param depth      null for the HUD's
     * @param background a dark background behind the text
     */
    @ConfigSerializable
    public record Element(Position position, @Nullable Double depth, double scale, boolean background) {
    }

    /**
     * Degrees right and down from the centre of view.
     */
    @ConfigSerializable
    public record Position(double yaw, double pitch) {
    }
}
