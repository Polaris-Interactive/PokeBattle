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
                                      @Nullable Creatures creatures, @Nullable Hud hud, @Nullable Staging staging,
                                      @Nullable Capture capture) {

    private static final Duration DEFAULT_CHECK_INTERVAL = Duration.ofSeconds(2);
    private static final String DEFAULT_STAGE_SPAWN = "stage";
    private static final Duration DEFAULT_RETURN_DELAY = Duration.ofSeconds(3);
    private static final int DEFAULT_GUARANTEED_BELOW = 2;
    private static final double DEFAULT_RING_RADIUS = 6.5;
    private static final String DEFAULT_RING_PARTICLE = "end_rod";
    private static final Duration DEFAULT_RING_INTERVAL = Duration.ofMillis(500);
    private static final int DEFAULT_RING_POINTS = 48;
    private static final Point DEFAULT_RING_CENTRE = new Point(4.4, 0, 0);
    private static final Duration DEFAULT_NUMBER_LIFE = Duration.ofMillis(1200);

    /**
     * The {@code staging} section, its missing keys at their defaults (see {@link #arenaOrDefault()}).
     */
    public Staging stagingOrDefault() {
        Staging section = this.staging;
        if (section == null)
            return new Staging(true, DEFAULT_RING_CENTRE, DEFAULT_RING_RADIUS, DEFAULT_RING_PARTICLE,
                    DEFAULT_RING_POINTS, DEFAULT_RING_INTERVAL, true);
        return new Staging(section.ring() == null || section.ring(),
                section.centre() == null ? DEFAULT_RING_CENTRE : section.centre(),
                section.radius() == null ? DEFAULT_RING_RADIUS : section.radius(),
                section.particle() == null ? DEFAULT_RING_PARTICLE : section.particle(),
                section.points() == null ? DEFAULT_RING_POINTS : section.points(),
                section.interval() == null ? DEFAULT_RING_INTERVAL : section.interval(),
                section.barrier() == null || section.barrier());
    }

    /**
     * Battles fought where they started (not in an arena): a ring drawn on the ground around the stage, seen only by
     * the player, and a barrier keeping them inside it. A player held by the HUD does not walk anyway.
     *
     * @param ring     whether the ring is drawn
     * @param centre   the ring's centre in the stage's frame (see {@link Stage})
     * @param radius   blocks; the barrier is the same circle
     * @param particle a Bukkit particle name ({@code end_rod}); one without extra data
     * @param points   particles per ring
     * @param interval how often the ring is drawn again
     * @param barrier  whether the player is kept inside the ring
     */
    @ConfigSerializable
    public record Staging(@Nullable Boolean ring, @Nullable Point centre, @Nullable Double radius,
                          @Nullable String particle, @Nullable Integer points, @Nullable Duration interval,
                          @Nullable Boolean barrier) {
    }

    /**
     * The {@code capture} section, its missing keys at their defaults (see {@link #arenaOrDefault()}).
     */
    public Capture captureOrDefault() {
        Capture section = this.capture;
        return new Capture(section == null || section.guaranteedBelow() == null
                ? DEFAULT_GUARANTEED_BELOW
                : section.guaranteedBelow());
    }

    /**
     * @param guaranteedBelow a ball always catches while the player's Pokédex counts fewer caught species than this
     *                        (onboarding, GDD: the first capture is guaranteed; the starter already counts one, hence
     *                        2); 0 for never
     */
    @ConfigSerializable
    public record Capture(@Nullable Integer guaranteedBelow) {
    }

    /**
     * The {@code arena} section, its missing keys at their defaults. Configurate builds the section with null fields
     * when an older config lacks it, and AstralCore cannot write durations back, so defaults are applied here, not
     * in the record.
     */
    public Arena arenaOrDefault() {
        Arena section = this.arena;
        return new Arena(section == null || section.stageSpawn() == null ? DEFAULT_STAGE_SPAWN : section.stageSpawn(),
                section == null || section.returnDelay() == null ? DEFAULT_RETURN_DELAY : section.returnDelay());
    }

    /**
     * Battles in arenas: instances run by PolarisInstances on instance servers.
     *
     * @param stageSpawn  the arena template's spawn point the battle is staged at (where the player's side stands,
     *                    facing the opponent); without it, the battle is laid in front of the arrival spawn
     * @param returnDelay after the battle ends, how long before the arena closes and the player goes back
     */
    @ConfigSerializable
    public record Arena(@Nullable String stageSpawn, @Nullable Duration returnDelay) {
    }

    /**
     * The {@code recovery} section, its missing keys at their defaults (see {@link #arenaOrDefault()}).
     */
    public Recovery recoveryOrDefault() {
        Recovery section = this.recovery;
        return new Recovery(section == null || section.checkInterval() == null
                ? DEFAULT_CHECK_INTERVAL
                : section.checkInterval());
    }

    /**
     * @param checkInterval how often this server checks that the workers of its battles still beat (a dead one's
     *                      battles are taken over by another worker, see the worker's heartbeat TTL)
     */
    @ConfigSerializable
    public record Recovery(@Nullable Duration checkInterval) {
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
     * @param listIcon           the creature icon on a team or target button, as a share of its strip's scale; 0 for
     *                           0.22
     * @param elements           where things go: {@code foe}, {@code ally}, {@code prompt}, {@code timer}, {@code log},
     *                           {@code info}, {@code hint}, the menu buttons {@code button-1}... (as many as wanted),
     *                           {@code back} and {@code next}. An element left out is not drawn
     */
    @ConfigSerializable
    public record Hud(boolean enabled, boolean closeable, double depth, Cursor cursor, boolean chatLog,
                      int logLines, Duration closeDelay, String hoverBackground, String disabledBackground,
                      String hoverSound, String clickSound, Bar bar, @Nullable Camera camera,
                      Map<String, Element> elements, double listIcon) {
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
                            @Nullable MoveEffects moves, @Nullable Hit hit) {

        /**
         * The {@code hit} section, its missing keys at their defaults.
         */
        public Hit hitOrDefault() {
            Hit section = this.hit;
            if (section == null)
                return new Hit(true, true, DEFAULT_NUMBER_LIFE, 0.8, 1.2);
            return new Hit(section.flash() == null || section.flash(),
                    section.numbers() == null || section.numbers(),
                    section.numberLife() == null ? DEFAULT_NUMBER_LIFE : section.numberLife(),
                    section.numberRise() == null ? 0.8 : section.numberRise(),
                    section.numberScale() == null ? 1.2 : section.numberScale());
        }

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
    /**
     * What a hit shows on the creature that takes it.
     *
     * @param flash       whether its model flashes red (BetterModel's damage tint)
     * @param numbers     whether the HP it lost rises above it ({@code hit-damage-ally}/{@code hit-damage-foe} in
     *                    {@code messages.yml}), seen only by the player
     * @param numberLife  how long a number stays
     * @param numberRise  blocks a number rises while it stays
     * @param numberScale its size
     */
    @ConfigSerializable
    public record Hit(@Nullable Boolean flash, @Nullable Boolean numbers, @Nullable Duration numberLife,
                      @Nullable Double numberRise, @Nullable Double numberScale) {
    }

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
     * @param referenceSize the biggest creature (height or width, in blocks) shots are laid out for: a bigger one is
     *                      framed from further back, its look height raised the same; 0 for 1.5
     * @param maxScale    the most a shot is pushed back for a big creature, as a factor; 0 for 4
     */
    @ConfigSerializable
    public record Camera(boolean enabled, double lookHeight, Duration maxSequence,
                         Map<String, Shot> shots, double referenceSize, double maxScale) {
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
