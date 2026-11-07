package com.astralrealms.poke.battle.configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import com.astralrealms.poke.battle.model.RewardCalculator;

/**
 * {@code config.yml}. Values the GDD leaves open live here with a documented default rather than in code.
 */
@ConfigSerializable
public record PokeBattleConfiguration(Settings settings, Start start, Battle battle, @Nullable Recovery recovery,
                                      @Nullable Arena arena, Rewards rewards, Dialog dialog, Stage stage,
                                      @Nullable Creatures creatures, @Nullable Hud hud) {

    private static final Recovery DEFAULT_RECOVERY = new Recovery(null);
    private static final Arena DEFAULT_ARENA = new Arena(null, null);

    /**
     * The {@code arena} section, or its defaults when an older config has none.
     */
    public Arena arenaOrDefault() {
        return this.arena == null ? DEFAULT_ARENA : this.arena;
    }

    /**
     * Battles in arenas: instances run by PolarisInstances on instance servers.
     *
     * @param stageSpawn  the arena template's spawn point the battle is staged at (where the player's side stands,
     *                    facing the opponent); without it, the battle is laid in front of the arrival spawn
     * @param returnDelay after the battle ends, how long before the arena closes and the player goes back
     */
    @ConfigSerializable
    public record Arena(String stageSpawn, Duration returnDelay) {

        // Configurate builds the record even when the section is absent from an older config: defaults per field
        public Arena {
            stageSpawn = stageSpawn == null ? "stage" : stageSpawn;
            returnDelay = returnDelay == null ? Duration.ofSeconds(3) : returnDelay;
        }
    }

    /**
     * The {@code recovery} section, or its defaults when an older config has none.
     */
    public Recovery recoveryOrDefault() {
        return this.recovery == null ? DEFAULT_RECOVERY : this.recovery;
    }

    /**
     * @param checkInterval how often this server checks that the workers of its battles still beat (a dead one's
     *                      battles are taken over by another worker, see the worker's heartbeat TTL)
     */
    @ConfigSerializable
    public record Recovery(Duration checkInterval) {

        // Configurate builds the record even when the section is absent from an older config: defaults per field
        public Recovery {
            checkInterval = checkInterval == null ? Duration.ofSeconds(2) : checkInterval;
        }
    }

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
     * @param cursor             the cursor the mouse steers (the camera itself is locked)
     * @param chatLog            whether the battle log also goes to the chat
     * @param logLines           log lines shown in the HUD
     * @param closeDelay         how long the HUD stays after the battle ended
     * @param camera             null for a still camera, where the player stood
     * @param elements           where things go: {@code foe}, {@code ally}, {@code prompt}, {@code timer}, {@code log},
     *                           {@code info}, {@code hint}, the menu buttons {@code button-1}... (as many as wanted),
     *                           {@code back} and {@code next}. An element left out is not drawn
     */
    @ConfigSerializable
    public record Hud(boolean enabled, boolean closeable, double depth, Cursor cursor, boolean chatLog,
                      int logLines, Duration closeDelay, String hoverBackground, String disabledBackground,
                      String hoverSound, String clickSound, Bar bar, @Nullable Camera camera,
                      Map<String, Element> elements) {
    }

    /**
     * @param range       how far from the centre it may go, in degrees each way
     * @param smoothing   ticks the client takes to glide it to each new position (0 to 3)
     */
    @ConfigSerializable
    public record Cursor(String text, float scale, Position range, float sensitivity, int smoothing) {
    }

    /**
     * Where a battle takes place, laid in front of where it started. Places are in blocks from there: {@code forward}
     * the way the player looked, {@code right} (left when negative), {@code up} from their feet.
     *
     * @param ally where the player's creature stands
     * @param foe  where the opponent stands
     */
    @ConfigSerializable
    public record Stage(Point ally, Point foe) {
    }

    /**
     * The two creatures standing on the stage, seen only by the player (PokeEntities). Without PokeEntities, or
     * disabled, the battle has no creatures in the world.
     *
     * @param animations by moment ({@code switch}, {@code move}, {@code damage}, {@code faint}): the model
     *                   animations to try, first found plays
     * @param cueSpacing the least time a beat the creatures play takes, so moments come one after the other
     * @param faintLinger how long a fainted creature stays before it goes
     * @param closeDelay how long the creatures stay after the battle ended
     * @param moveSpacing the least time a move takes instead of {@code cueSpacing} (its wind-up and effect); null
     *                    for {@code cueSpacing}
     * @param moves      the effects a creature's moves play on stage; null for none
     */
    @ConfigSerializable
    public record Creatures(boolean enabled, Map<String, List<String>> animations, Duration cueSpacing,
                            @Nullable Duration moveSpacing, Duration faintLinger, Duration closeDelay,
                            @Nullable MoveEffects moves) {

        public List<String> animations(String moment) {
            List<String> names = this.animations == null ? null : this.animations.get(moment);
            return names == null ? List.of() : names;
        }
    }

    /**
     * A move's effect on stage: an AstralSkill skill cast by the attacking creature towards the other one. The skills
     * are content (poke-content), found by id.
     *
     * @param skills the skill ids to try, first found plays: {@code {move}} is the move id, {@code {type}} and
     *               {@code {category}} its type and category ids
     * @param height   blocks above a creature's feet the effect leaves from and aims at
     * @param statUp   the skill a creature casts when one of its stats rises; empty for none
     * @param statDown the skill a creature casts when one of its stats falls; empty for none
     * @param releaseMarker the model animation marker ({@code pokesmp:<name>} timeline keyframe) the effect waits
     *                      for; empty or absent from the animation: the effect leaves with the animation's start
     */
    @ConfigSerializable
    public record MoveEffects(boolean enabled, List<String> skills, double height, @Nullable String statUp,
                              @Nullable String statDown, @Nullable String releaseMarker) {

        public List<String> skills() {
            return this.skills == null ? List.of() : this.skills;
        }
    }

    /**
     * The camera moving around the stage as the battle goes (see {@link Stage} for places).
     *
     * @param lookHeight  blocks above a creature's feet a shot looks at
     * @param maxSequence the longest run of beats; what does not fit shows at once
     * @param shots       by {@code idle} (while choosing) and by cue ({@code ally-move}, {@code foe-faint}...); a cue
     *                    without a shot leaves the camera where it is
     */
    @ConfigSerializable
    public record Camera(boolean enabled, double lookHeight, Duration maxSequence,
                         Map<String, Shot> shots) {
    }

    @ConfigSerializable
    public record Point(double forward, double right, double up) {
    }

    /**
     * Where the camera goes and what it looks at.
     *
     * @param lookAt {@code ally}, {@code foe}, or {@code center} (between them)
     * @param glide  ticks to get there, 0 to cut
     * @param hold   ticks it stays before the next shot
     */
    @ConfigSerializable
    public record Shot(Point from, String lookAt, int glide, int hold) {
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
