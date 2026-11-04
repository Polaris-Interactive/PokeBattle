package com.astralrealms.poke.battle.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.astralrealms.core.packet.Packet;
import com.astralrealms.poke.battle.protocol.ChoiceRequest;
import com.astralrealms.poke.core.mechanics.generation.GeneratedCreature;
import com.astralrealms.poke.core.model.Pokemon;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.Getter;
import lombok.Setter;

/**
 * A battle shown by this server: one player's side against a wild creature. Worker packets are fed to it in order
 * ({@link #sequence}), under its monitor; every mutable field is guarded by it.
 * <p>
 * Team members are named by their 1-based index in the start team ({@code p1a: 2}): display names are resolved here,
 * never sent to the simulator.
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

    private final UUID id;
    private final String format;
    private final UUID playerId;
    private final List<Ally> allies;
    private final List<Foe> foes;
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

    public Battle(UUID id, String format, UUID playerId, List<Ally> allies, List<Foe> foes) {
        this.id = id;
        this.format = format;
        this.playerId = playerId;
        this.allies = List.copyOf(allies);
        this.foes = List.copyOf(foes);
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
