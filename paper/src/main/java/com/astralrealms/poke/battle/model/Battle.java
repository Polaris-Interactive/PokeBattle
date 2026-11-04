package com.astralrealms.poke.battle.model;

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

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;

/**
 * A battle shown by this server: one player's side against a wild creature. Worker packets are fed to it in order
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

    public record Foe(String label, GeneratedCreature creature) {
    }

    /**
     * What the player may do besides moves and switches.
     *
     * @param wildRoll     the opponent as rolled, redeemed when caught; null when it cannot be caught
     * @param captureFlags facts about the encounter for ball rules
     * @param metAt        where a caught creature was met
     */
    public record Options(boolean capture, boolean bag, boolean flee, @Nullable SignedRoll wildRoll,
                          Set<String> captureFlags, String metAt) {

        public Options {
            captureFlags = Set.copyOf(captureFlags);
        }
    }

    /**
     * An item taken from the player's inventory for a choice: given back if the battle refuses that choice.
     */
    public record Spent(int rqid, String itemId) {
    }

    private final UUID id;
    private final String format;
    private final UUID playerId;
    private final List<Ally> allies;
    private final List<Foe> foes;
    private final Options options;
    /** Display names of the items used in this battle, for the log (bag items may have no bundle name). */
    private final Map<String, Component> itemNames = new ConcurrentHashMap<>();
    private final SequenceBuffer<Packet> sequence = new SequenceBuffer<>();
    private final BattleTracker tracker = new BattleTracker(PLAYER_SIDE);

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
    /** The item spent on the pending choice, not confirmed by the worker yet. */
    @Setter
    private @Nullable Spent spent;

    public Battle(UUID id, String format, UUID playerId, List<Ally> allies, List<Foe> foes, Options options) {
        this.id = id;
        this.format = format;
        this.playerId = playerId;
        this.allies = List.copyOf(allies);
        this.foes = List.copyOf(foes);
        this.options = options;
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
