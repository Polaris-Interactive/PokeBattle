package com.astralrealms.poke.battle.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.astralrealms.core.packet.Packet;
import com.astralrealms.poke.battle.protocol.ChoiceRequest;
import com.astralrealms.poke.core.mechanics.generation.GeneratedCreature;
import com.astralrealms.poke.core.mechanics.generation.SignedRoll;
import com.astralrealms.poke.core.model.Pokemon;
import com.astralrealms.poke.core.team.TrainerMember;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;

/**
 * A battle shown by this server: one player's side against a wild creature or an NPC trainer's team. Worker packets are fed to it in order
 * ({@link #sequence}), under its monitor; every mutable field is guarded by it.
 * <p>
 * Team members are named by their 1-based index in the start team ({@code p1a: 2}): display names are resolved here,
 * never sent to the simulator. Standing members come first; fainted ones follow, only there to be revived.
 */
@Getter
public class Battle {

    public static final String PLAYER_SIDE = "p1";
    public static final String FOE_SIDE = "p2";

    public enum State {
        /** Sent to the start queue, no worker reply yet. */
        REQUESTED,
        RUNNING,
        /** The worker said it ended: being settled, nothing else is accepted. */
        ENDED,
    }

    /**
     * One of the player's creatures, as it entered the battle.
     */
    public record Ally(String label, Pokemon pokemon) {
    }

    /**
     * One of the opponent's creatures, as much as this server shows and rewards (the worker has the whole set).
     *
     * @param nickname display name given by content, null for the species name
     */
    public record Foe(String label, String species, @Nullable String form, int level, @Nullable String nickname) {

        public static Foe of(String label, GeneratedCreature creature) {
            return new Foe(label, creature.species(), creature.form(), creature.level(), null);
        }

        public static Foe of(String label, TrainerMember member) {
            return new Foe(label, member.species(), member.form(), member.level(),
                    member.nickname() == null || member.nickname().isBlank() ? null : member.nickname());
        }

        /**
         * The same creature under another label.
         */
        public Foe labelled(String label) {
            return new Foe(label, this.species, this.form, this.level, this.nickname);
        }
    }

    /**
     * What the player may do besides moves and switches, and who the opponent is. Kept in Redis with the battle
     * ({@link BattleContext}): a field added here reads as null from a context written before it.
     *
     * @param wildRoll             the opponent as rolled, redeemed when caught; null when it cannot be caught
     * @param captureFlags         facts about the encounter for ball rules
     * @param metAt                where a caught creature was met
     * @param trainer              the NPC trainer's name, null for a wild battle
     * @param experienceMultiplier the request's, null for none (1.0)
     * @param tag                  the request's tag, given back by the battle's events
     * @param standIns             the player's side are stand-ins, not stored creatures: nothing is written when it
     *                             ends; null for false
     */
    public record Options(boolean capture, boolean bag, boolean flee, @Nullable SignedRoll wildRoll,
                          Set<String> captureFlags, String metAt, @Nullable String trainer,
                          @Nullable Double experienceMultiplier, @Nullable String tag, @Nullable Boolean standIns) {

        public Options {
            captureFlags = captureFlags == null ? Set.of() : Set.copyOf(captureFlags);
        }

        /**
         * Options of a battle fought by the player's stored creatures.
         */
        public Options(boolean capture, boolean bag, boolean flee, @Nullable SignedRoll wildRoll,
                       Set<String> captureFlags, String metAt, @Nullable String trainer,
                       @Nullable Double experienceMultiplier, @Nullable String tag) {
            this(capture, bag, flee, wildRoll, captureFlags, metAt, trainer, experienceMultiplier, tag, null);
        }

        /**
         * A wild battle's options with no trainer, multiplier or tag.
         */
        public Options(boolean capture, boolean bag, boolean flee, @Nullable SignedRoll wildRoll,
                       Set<String> captureFlags, String metAt) {
            this(capture, bag, flee, wildRoll, captureFlags, metAt, null, null, null);
        }

        public double experience() {
            return this.experienceMultiplier == null ? 1.0 : this.experienceMultiplier;
        }

        /**
         * Whether the player's side are stand-ins: the battle writes nothing.
         */
        public boolean foughtByStandIns() {
            return Boolean.TRUE.equals(this.standIns);
        }
    }

    /**
     * An item taken from the player's inventory for a choice: given back if the battle refuses that choice.
     *
     * @param key the item's global key
     */
    public record Spent(int rqid, String key) {
    }

    private final UUID id;
    private final String format;
    private final UUID playerId;
    private final List<Ally> allies;
    private final List<Foe> foes;
    private final Options options;
    /**
     * Display names of the items used in this battle by battle engine id, as the log names them (bag items may have no
     * bundle name).
     */
    private final Map<String, Component> itemNames = new ConcurrentHashMap<>();
    /** Global keys of the items this shard sent in choices, by battle engine id: what the engine's ids come back as. */
    private final Map<String, String> itemKeys = new ConcurrentHashMap<>();
    /** Starts over when the battle moves to another worker or shard: they number from 0 again. */
    private SequenceBuffer<Packet> sequence = new SequenceBuffer<>();
    /** Starts over on a resync, which brings the whole battle again. */
    private BattleTracker tracker = new BattleTracker(PLAYER_SIDE);

    @Setter
    private volatile State state = State.REQUESTED;
    @Setter
    private volatile @Nullable String workerId;
    /** The request the player has to answer, null while waiting for the turn. */
    @Setter
    private @Nullable ChoiceRequest pending;
    /** A choice for {@link #pending} was sent and is not refused yet. */
    @Setter
    private boolean chosen;
    /** Stop the battle once a worker has it (an abort asked before the start reply). */
    @Setter
    private @Nullable String abortReason;
    @Setter
    private @Nullable ScheduledTask disconnectTask;
    /**
     * Items spent on recent choices, by request id, until known used. A choice that reaches the worker too late (the
     * turn timer answered for the player) is refused after the next request came: its item is still given back.
     */
    @Getter(AccessLevel.NONE)
    private final Map<Integer, Spent> spent = new HashMap<>();
    /** Its worker died: another one is being asked to take it over. */
    @Setter
    private volatile boolean recovering;
    /** Taken over from another shard: the next resync opens the battle for the player instead of updating it. */
    @Setter
    private volatile boolean adopted;

    public Battle(UUID id, String format, UUID playerId, List<Ally> allies, List<Foe> foes, Options options) {
        this.id = id;
        this.format = format;
        this.playerId = playerId;
        this.allies = List.copyOf(allies);
        this.foes = List.copyOf(foes);
        this.options = options;
    }

    /**
     * Expects the worker's packets from 0 again: before asking a new worker or shard to take it over.
     */
    public void restartSequence() {
        this.sequence = new SequenceBuffer<>();
    }

    /**
     * Forgets what the battle showed so far: a resync replays all of it.
     */
    public void restartTracker() {
        this.tracker = new BattleTracker(PLAYER_SIDE);
    }

    /**
     * An item taken for the choice answering request {@link Spent#rqid}.
     */
    public void spend(Spent spent) {
        this.spent.put(spent.rqid(), spent);
    }

    /**
     * The item spent on the choice for {@code rqid}, refused: no longer spent.
     */
    public @Nullable Spent unspend(int rqid) {
        return this.spent.remove(rqid);
    }

    /**
     * Request {@code rqid} came: the choices before the previous request were played, or refused already (a refusal
     * of the previous one may still come).
     */
    public void requested(int rqid) {
        this.spent.keySet().removeIf(spentRqid -> spentRqid < rqid - 1);
    }

    public static String label(int index) {
        return Integer.toString(index + 1);
    }

    public Optional<Ally> ally(String label) {
        return this.allies.stream().filter(ally -> ally.label().equals(label)).findFirst();
    }

    public Optional<Foe> foe(String label) {
        return this.foes.stream().filter(foe -> foe.label().equals(label)).findFirst();
    }
}
