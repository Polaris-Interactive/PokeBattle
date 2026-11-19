package com.astralrealms.poke.battle.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.core.packet.Packet;
import com.astralrealms.core.placeholder.container.PlaceholderContainer;
import com.astralrealms.poke.battle.PokeBattle;
import com.astralrealms.poke.battle.api.event.BattleEndEvent;
import com.astralrealms.poke.battle.api.event.BattleResumeEvent;
import com.astralrealms.poke.battle.api.event.BattleStartEvent;
import com.astralrealms.poke.battle.api.model.BattleOutcome;
import com.astralrealms.poke.battle.api.model.BattleRequest;
import com.astralrealms.poke.battle.api.model.BattleRules;
import com.astralrealms.poke.battle.api.model.BattleStartResult;
import com.astralrealms.poke.battle.api.model.TrainerOpponent;
import com.astralrealms.poke.battle.api.service.BattleService;
import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;
import com.astralrealms.poke.battle.configuration.PokeBattleMessages;
import com.astralrealms.poke.battle.model.Battle;
import com.astralrealms.poke.battle.model.BattleContext;
import com.astralrealms.poke.battle.model.BattleTracker;
import com.astralrealms.poke.battle.model.Names;
import com.astralrealms.poke.battle.model.RewardCalculator;
import com.astralrealms.poke.battle.network.BattleTransport;
import com.astralrealms.poke.battle.network.packet.BattleChoiceErrorPacket;
import com.astralrealms.poke.battle.network.packet.BattleEndedPacket;
import com.astralrealms.poke.battle.network.packet.BattleMemberState;
import com.astralrealms.poke.battle.network.packet.BattleRequestPacket;
import com.astralrealms.poke.battle.network.packet.BattleSide;
import com.astralrealms.poke.battle.network.packet.BattleStartPacket;
import com.astralrealms.poke.battle.network.packet.BattleStartReplyPacket;
import com.astralrealms.poke.battle.network.packet.BattleTeamState;
import com.astralrealms.poke.battle.network.packet.BattleUpdatePacket;
import com.astralrealms.poke.battle.protocol.ChoiceRequest;
import com.astralrealms.poke.battle.protocol.Condition;
import com.astralrealms.poke.battle.protocol.ProtocolLine;
import com.astralrealms.poke.battle.protocol.SlotChoices;
import com.astralrealms.poke.battle.render.BattleLogRenderer;
import com.astralrealms.poke.battle.ui.BattleBeat;
import com.astralrealms.poke.battle.ui.BattleCue;
import com.astralrealms.poke.battle.ui.BattleStage;
import com.astralrealms.poke.battle.ui.BattleUi;
import com.astralrealms.poke.battle.ui.ChoiceDialogs;
import com.astralrealms.poke.battle.ui.ChoiceView;
import com.astralrealms.poke.battle.ui.FieldView;
import com.astralrealms.poke.core.PokeCoreAPI;
import com.astralrealms.poke.core.api.model.CaptureAttempt;
import com.astralrealms.poke.core.api.model.CreationOptions;
import com.astralrealms.poke.core.api.model.CreationResult;
import com.astralrealms.poke.core.mechanics.PokemonStats;
import com.astralrealms.poke.core.mechanics.StatCalculator;
import com.astralrealms.poke.core.mechanics.capture.BallDefinition;
import com.astralrealms.poke.core.mechanics.capture.CaptureContext;
import com.astralrealms.poke.core.mechanics.capture.CaptureResult;
import com.astralrealms.poke.core.mechanics.generation.GeneratedCreature;
import com.astralrealms.poke.core.mechanics.generation.SignedRoll;
import com.astralrealms.poke.core.mechanics.item.ItemKeys;
import com.astralrealms.poke.core.mechanics.progression.BattleCondition;
import com.astralrealms.poke.core.mechanics.progression.ExperienceGain;
import com.astralrealms.poke.core.mechanics.progression.Progression;
import com.astralrealms.poke.core.mechanics.storage.StorageSlots;
import com.astralrealms.poke.core.model.MoveSlot;
import com.astralrealms.poke.core.model.Pokemon;
import com.astralrealms.poke.core.model.stat.StatSpread;
import com.astralrealms.poke.core.model.storage.StorageLimits;
import com.astralrealms.poke.core.model.storage.StoragePosition;
import com.astralrealms.poke.core.model.storage.StorageResult;
import com.astralrealms.poke.core.model.storage.TrainerStorage;
import com.astralrealms.poke.core.species.Species;
import com.astralrealms.poke.core.species.SpeciesVariant;
import com.astralrealms.poke.core.species.SpeciesRegistry;
import com.astralrealms.poke.core.team.BattleSets;
import com.astralrealms.poke.core.team.PackedSet;
import com.astralrealms.poke.core.team.PackedTeam;
import com.astralrealms.poke.core.team.TrainerMember;
import com.astralrealms.poke.core.utils.UuidV7;

import net.kyori.adventure.text.Component;

/**
 * The battles this server shows, from the start request to the settlement.
 * <ul>
 *     <li><b>Start:</b> reserve the player here, take the network-wide lock, publish to the start queue (the first
 *     worker with room takes it).</li>
 *     <li><b>Running:</b> worker packets are put back in order per battle, then rendered (log), tracked (field,
 *     defeats) and answered (choice dialog). Choices are checked against the pending request before they leave.</li>
 *     <li><b>End:</b> the worker's final team states and the rewards are written by PokeCore in one transaction,
 *     then the lock is released. Only one shard settles a battle (claimed in Redis).</li>
 *     <li><b>Recovery:</b> a battle whose worker stops beating is taken over by another worker, which replays it and
 *     sends it again from sequence 0 (a resync). A player who joins while a battle of theirs runs elsewhere (they
 *     changed server, or its shard stopped) gets it here: the battle's context is in Redis, the worker resends it.</li>
 * </ul>
 * Packets arrive on messaging threads; a battle's state is only touched under its monitor, and the player only on
 * its own thread.
 */
public class BattleServiceImpl implements BattleService, ChoiceDialogs.Handler {

    /** A capture that cannot fail: every shake, then caught. */
    private static final CaptureResult GUARANTEED_CAPTURE = new CaptureResult(true, 3);

    private static final int MAX_TEAM = 6;
    private static final String WILD_SIDE_NAME = "wild";
    private static final String WILD_AI = "wild";
    private static final String RUN = "run";
    /** First line of a worker update carrying the whole battle again (see the worker's RESYNC_LINE). */
    private static final String RESYNC_LINE = "|polaris-resync|";

    private final PokeBattle plugin;
    private final BattleTransport transport;
    private final BattleLocks locks;
    private final BattleRecords records;
    private final String shardId;
    private final Map<UUID, Battle> battles = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> byPlayer = new ConcurrentHashMap<>();
    private final BattleBag bag = new BattleBag();
    /**
     * Battles released after their worker did not confirm a stop, for {@code battle.end-timeout}: an end that comes
     * after all (a capture in the last turn) is still settled.
     */
    private final Map<UUID, Battle> aborted = new ConcurrentHashMap<>();
    /** By battle, the doubles choice last shown (the slot being chosen for): typed choices carry on from it. */
    private final Map<UUID, ChoiceView> slotViews = new ConcurrentHashMap<>();
    /** Settlements being written: a stopping server waits for them (see {@link #shutdown}). */
    private final Set<CompletableFuture<?>> settling = ConcurrentHashMap.newKeySet();

    public BattleServiceImpl(PokeBattle plugin, BattleTransport transport, BattleLocks locks, BattleRecords records,
                             String shardId) {
        this.plugin = plugin;
        this.transport = transport;
        this.locks = locks;
        this.records = records;
        this.shardId = shardId;
    }

    // ---- Start ----

    @Override
    public CompletableFuture<BattleStartResult> start(BattleRequest request) {
        Player player = request.player();
        UUID playerId = player.getUniqueId();
        SpeciesRegistry registry = PokeCoreAPI.get().species();
        List<Battle.Ally> allies = request.standIns() != null
                ? standInAllies(request.standIns(), registry)
                : this.partyAllies(playerId, request.lead(), registry);
        if (allies.isEmpty())
            return CompletableFuture.completedFuture(BattleStartResult.NO_USABLE_CREATURE);

        UUID battleId = UuidV7.generate();
        BattleRules rules = request.rules();
        String metAt = request.place() != null ? request.place() : player.getWorld().getName();
        List<Battle.Foe> foes = new ArrayList<>();
        List<PackedSet> foeSets = new ArrayList<>();
        Battle.Options options;
        BattleStartPacket packet;
        Battle battle;
        try {
            SignedRoll wild = request.wild();
            TrainerOpponent trainer = request.trainer();
            List<Pokemon> standIns = request.standIns();
            if (wild != null) {
                foes.add(Battle.Foe.of(Battle.label(0), wild.creature()));
                foeSets.add(BattleSets.of(wild.creature(), Battle.label(0), registry));
                // Stand-ins never catch: a caught creature would be written
                options = new Battle.Options(rules.capture() && standIns == null, rules.bag(), rules.flee(), wild,
                        request.captureFlags(), metAt, null, rules.experienceMultiplier(), request.tag(),
                        standIns == null ? null : true);
            } else {
                List<String> errors = PokeCoreAPI.get().creation().validate(trainer.team());
                if (!errors.isEmpty()) {
                    this.plugin.getSLF4JLogger().error("Battle {} for {}: the team of trainer {} is invalid: {}",
                            battleId, player.getName(), trainer.name(), String.join("; ", errors));
                    return CompletableFuture.completedFuture(BattleStartResult.ERROR);
                }
                for (TrainerMember member : trainer.team()) {
                    // Defaults rolled once, here: the worker gets the whole set, this server what it shows
                    TrainerMember completed = PokeCoreAPI.get().creation().complete(member);
                    String label = Battle.label(foes.size());
                    foes.add(Battle.Foe.of(label, completed));
                    foeSets.add(BattleSets.of(completed, label, registry));
                }
                // Never capture a trainer's creature, whatever the rules say
                options = new Battle.Options(false, rules.bag(), rules.flee(), null, Set.of(), metAt,
                        trainerName(trainer.name()), rules.experienceMultiplier(), request.tag(),
                        standIns == null ? null : true);
            }
            battle = new Battle(battleId, request.format(), playerId, allies, foes, options);
            packet = this.startPacket(battle, player.getName(), registry, foeSets,
                    trainer == null ? WILD_AI : trainer.ai());
        } catch (IllegalArgumentException e) {
            this.plugin.getSLF4JLogger().error("Battle {} for {} cannot be built", battleId, player.getName(), e);
            return CompletableFuture.completedFuture(BattleStartResult.ERROR);
        }

        if (this.byPlayer.putIfAbsent(playerId, battleId) != null)
            return CompletableFuture.completedFuture(BattleStartResult.ALREADY_IN_BATTLE);
        this.battles.put(battleId, battle);
        if (request.stage() != null)
            this.plugin.stages().place(battleId, request.stage(), request.spots(), this.plugin.configuration());

        PokeBattleConfiguration configuration = this.plugin.configuration();
        return this.locks.acquire(playerId, battleId, configuration.battle().lockTtl())
                .thenCompose(acquired -> {
                    if (!acquired) {
                        this.forget(battle);
                        return CompletableFuture.completedFuture(BattleStartResult.ALREADY_IN_BATTLE);
                    }
                    // Before a worker can run it: another shard may have to take it over
                    BattleContext.Staging staging = request.stage() == null
                            ? null
                            : BattleContext.Staging.of(request.stage(), request.spots());
                    return this.records.saveContext(BattleContext.of(battle, staging), configuration.battle().lockTtl())
                            .thenCompose(_ -> this.transport.start(packet, configuration.start().timeout(),
                                    configuration.start().attempts()))
                            .thenApply(reply -> this.onStartReply(battle, player, reply));
                })
                .exceptionally(throwable -> {
                    this.plugin.getSLF4JLogger().error("Starting battle {} for {} failed", battleId, player.getName(),
                            throwable);
                    this.discard(battle);
                    return BattleStartResult.ERROR;
                });
    }

    /**
     * The player's party as it fights: standing creatures first (the lead must fight), the one the player threw at the
     * wild creature leading; fainted ones follow, only there to be revived from the bag. Empty when no creature can
     * fight.
     */
    private List<Battle.Ally> partyAllies(UUID playerId, @Nullable UUID lead, SpeciesRegistry registry) {
        TrainerStorage storage = PokeCoreAPI.get().trainers().cached(playerId).orElse(null);
        if (storage == null)
            return List.of();
        List<Pokemon> standing = new ArrayList<>();
        List<Pokemon> fainted = new ArrayList<>();
        for (Pokemon pokemon : storage.partyMembers())
            (PokemonStats.currentHp(pokemon, registry) > 0 ? standing : fainted).add(pokemon);
        if (standing.isEmpty())
            return List.of();
        // The creature the player threw at it goes first, the others keep their order
        if (lead != null)
            standing.stream()
                    .filter(pokemon -> pokemon.id().equals(lead))
                    .findFirst()
                    .ifPresent(first -> {
                        standing.remove(first);
                        standing.addFirst(first);
                    });
        List<Battle.Ally> allies = new ArrayList<>();
        for (Pokemon pokemon : standing)
            if (allies.size() < MAX_TEAM)
                allies.add(new Battle.Ally(Battle.label(allies.size()), pokemon));
        for (Pokemon pokemon : fainted)
            if (allies.size() < MAX_TEAM)
                allies.add(new Battle.Ally(Battle.label(allies.size()), pokemon));
        return allies;
    }

    /**
     * Stand-ins as they fight, in the given order; empty when none can fight (the first must).
     */
    static List<Battle.Ally> standInAllies(List<Pokemon> standIns, SpeciesRegistry registry) {
        if (standIns.isEmpty() || PokemonStats.currentHp(standIns.getFirst(), registry) <= 0)
            return List.of();
        List<Battle.Ally> allies = new ArrayList<>();
        for (Pokemon pokemon : standIns)
            if (allies.size() < MAX_TEAM)
                allies.add(new Battle.Ally(Battle.label(allies.size()), pokemon));
        return allies;
    }

    /**
     * A trainer's name as the simulator gets it: a protocol line cannot hold {@code |} or a line break.
     */
    static String trainerName(String name) {
        String cleaned = name.replaceAll("[|\\r\\n]+", " ").strip();
        return cleaned.isEmpty() ? "?" : cleaned;
    }

    /**
     * @param foeSets the opponent's team, in label order
     * @param foeAi   the AI that plays it
     */
    private BattleStartPacket startPacket(Battle battle, String playerName, SpeciesRegistry registry,
                                          List<PackedSet> foeSets, String foeAi) {
        List<PackedSet> sets = new ArrayList<>();
        List<BattleMemberState> states = new ArrayList<>();
        for (Battle.Ally ally : battle.allies()) {
            Pokemon pokemon = ally.pokemon();
            sets.add(BattleSets.of(pokemon, ally.label(), registry));
            states.add(new BattleMemberState(PokemonStats.currentHp(pokemon, registry),
                    pokemon.status() == null ? "" : pokemon.status(),
                    pokemon.moves().stream().map(MoveSlot::pp).toList(),
                    pokemon.moves().stream()
                            .map(slot -> registry.findMove(slot.move())
                                    .map(move -> move.maxPp(slot.ppUps()))
                                    .orElse(slot.pp()))
                            .toList()));
        }
        String foeName = battle.options().trainer() != null ? battle.options().trainer() : WILD_SIDE_NAME;
        List<BattleSide> sides = List.of(
                new BattleSide(Battle.PLAYER_SIDE, playerName, battle.playerId(), PackedTeam.pack(sets), "", states),
                new BattleSide(Battle.FOE_SIDE, foeName, null, PackedTeam.pack(foeSets), foeAi, List.of()));
        return new BattleStartPacket(battle.id(), this.shardId, battle.format(), "", sides,
                this.plugin.configuration().battle().turnTimerSeconds(), battle.options().bag(),
                battle.options().capture());
    }

    private BattleStartResult onStartReply(Battle battle, Player player, @Nullable BattleStartReplyPacket reply) {
        if (reply == null) {
            this.plugin.getSLF4JLogger().warn("No battle worker took battle {} for {}", battle.id(), player.getName());
            this.discard(battle);
            return BattleStartResult.NO_WORKER;
        }
        if (!reply.accepted()) {
            this.plugin.getSLF4JLogger().warn("Worker {} refused battle {} for {}: {}", reply.workerId(), battle.id(),
                    player.getName(), reply.reason());
            this.discard(battle);
            return BattleStartResult.REFUSED;
        }

        String abortReason;
        synchronized (battle) {
            battle.workerId(reply.workerId());
            if (battle.state() == Battle.State.REQUESTED)
                battle.state(Battle.State.RUNNING);
            abortReason = battle.abortReason();
            this.showField(battle);
            // A request that came before the reply could not be answered yet
            this.openDialog(battle);
        }
        this.plugin.debug("Battle {} for {} runs on worker {}", battle.id(), player.getName(), reply.workerId());
        if (abortReason != null)
            this.abort(battle.id(), abortReason);
        PokeBattleMessages.REOPEN_HINT.message(player);
        Bukkit.getPluginManager().callEvent(new BattleStartEvent(battle.id(), player, battle.format(),
                battle.options().tag()));
        return BattleStartResult.STARTED;
    }

    // ---- Queries ----

    @Override
    public Optional<UUID> battleOf(UUID playerId) {
        return Optional.ofNullable(this.byPlayer.get(playerId));
    }

    private @Nullable Battle battleFor(Player player) {
        UUID battleId = this.byPlayer.get(player.getUniqueId());
        return battleId == null ? null : this.battles.get(battleId);
    }

    // ---- Worker packets ----

    /**
     * A packet from the shard exchange: put back in order, then handled, under the battle's monitor.
     */
    public void receive(Packet packet) {
        UUID battleId;
        long seq;
        switch (packet) {
            case BattleUpdatePacket update -> {
                battleId = update.battleId();
                seq = update.seq();
            }
            case BattleRequestPacket request -> {
                battleId = request.battleId();
                seq = request.seq();
            }
            case BattleChoiceErrorPacket error -> {
                battleId = error.battleId();
                seq = error.seq();
            }
            case BattleEndedPacket ended -> {
                battleId = ended.battleId();
                seq = ended.seq();
            }
            default -> {
                return;
            }
        }

        Battle battle = this.battles.get(battleId);
        if (battle == null) {
            Battle released = this.aborted.get(battleId);
            if (released != null) {
                synchronized (released) {
                    for (Packet ready : released.sequence().offer(seq, packet))
                        this.processLate(released, ready);
                }
                return;
            }
            this.plugin.debug("Packet {} for unknown battle {}", packet.getClass().getSimpleName(), battleId);
            return;
        }
        synchronized (battle) {
            for (Packet ready : battle.sequence().offer(seq, packet))
                this.process(battle, ready);
        }
    }

    private void process(Battle battle, Packet packet) {
        if (battle.state() == Battle.State.ENDED) {
            // Released while its stop was not confirmed: its end still counts
            if (this.aborted.get(battle.id()) == battle)
                this.processLate(battle, packet);
            return;
        }
        switch (packet) {
            case BattleUpdatePacket update -> this.onUpdate(battle, update);
            case BattleRequestPacket request -> this.onRequest(battle, request);
            case BattleChoiceErrorPacket error -> this.onChoiceError(battle, error);
            case BattleEndedPacket ended -> {
                battle.state(Battle.State.ENDED);
                this.cancelDisconnect(battle);
                this.settle(battle, ended);
            }
            default -> {
            }
        }
    }

    /**
     * A packet for a battle released before its worker confirmed the stop: what it shows is only tracked (the player
     * no longer sees it), and its end is settled. Under the battle's monitor.
     */
    private void processLate(Battle battle, Packet packet) {
        switch (packet) {
            case BattleUpdatePacket update when update.side().equals(Battle.PLAYER_SIDE) -> {
                List<String> lines = update.lines();
                if (!lines.isEmpty() && lines.getFirst().equals(RESYNC_LINE)) {
                    battle.restartTracker();
                    lines = lines.subList(1, lines.size());
                }
                for (String raw : lines) {
                    ProtocolLine line = ProtocolLine.parse(raw);
                    if (line != null)
                        battle.tracker().accept(line);
                }
            }
            case BattleEndedPacket ended -> {
                if (!this.aborted.remove(battle.id(), battle))
                    return;
                this.plugin.getSLF4JLogger().warn("Battle {}: its end came after it was released, settling it",
                        battle.id());
                this.settle(battle, ended);
            }
            default -> {
            }
        }
    }

    private void onUpdate(Battle battle, BattleUpdatePacket update) {
        if (!update.side().equals(Battle.PLAYER_SIDE))
            return;
        if (!update.lines().isEmpty() && update.lines().getFirst().equals(RESYNC_LINE)) {
            this.resync(battle, update);
            return;
        }
        Player player = Bukkit.getPlayer(battle.playerId());
        BattleLogRenderer renderer = new BattleLogRenderer(battle, player == null ? null : player.locale());
        // One beat per cue, with the field right after it, so the UI shows each moment in turn
        List<BattleBeat> beats = new ArrayList<>();
        List<Component> log = new ArrayList<>();
        for (String raw : update.lines()) {
            ProtocolLine line = ProtocolLine.parse(raw);
            if (line == null)
                continue;
            battle.tracker().accept(line);
            if (player == null)
                continue;
            Component rendered = renderer.render(line);
            if (rendered != null)
                log.add(rendered);
            BattleCue cue = BattleCue.of(line);
            if (cue != null) {
                beats.add(BattleBeat.of(cue, line, FieldView.of(battle, player.locale()), log));
                log.clear();
            }
        }
        if (player == null)
            return;
        if (!log.isEmpty() || beats.isEmpty())
            beats.add(new BattleBeat(null, null, FieldView.of(battle, player.locale()), log));
        this.withUi(battle.playerId(), (ui, viewer) -> beats.forEach(beat -> ui.update(viewer, beat)));
    }

    /**
     * The whole battle again, from a worker that took it over or because this server took it over: the view starts
     * over from it, silently, and the player sees where the battle stands. Under the battle's monitor.
     */
    private void resync(Battle battle, BattleUpdatePacket update) {
        battle.restartTracker();
        for (String raw : update.lines().subList(1, update.lines().size())) {
            ProtocolLine line = ProtocolLine.parse(raw);
            if (line != null)
                battle.tracker().accept(line);
        }
        boolean adopted = battle.adopted();
        battle.adopted(false);
        Player player = Bukkit.getPlayer(battle.playerId());
        if (player == null)
            return;
        if (adopted) {
            this.showField(battle);
            return;
        }
        BattleBeat beat = new BattleBeat(null, null, FieldView.of(battle, player.locale()), List.of());
        this.withUi(battle.playerId(), (ui, viewer) -> ui.update(viewer, beat));
    }

    private void onRequest(Battle battle, BattleRequestPacket packet) {
        if (!packet.side().equals(Battle.PLAYER_SIDE))
            return;
        ChoiceRequest request;
        try {
            request = ChoiceRequest.parse(packet.request());
        } catch (IllegalArgumentException e) {
            this.plugin.getSLF4JLogger().error("Battle {}: unreadable request", battle.id(), e);
            return;
        }
        battle.pending(request);
        battle.chosen(false);
        // A new request: the items spent on older choices were used
        battle.requested(request.rqid());
        Duration ttl = this.plugin.configuration().battle().lockTtl();
        this.locks.refresh(battle.playerId(), battle.id(), ttl)
                .exceptionally(throwable -> {
                    this.plugin.getSLF4JLogger().warn("Battle {}: lock refresh failed", battle.id(), throwable);
                    return false;
                });
        this.records.refreshContext(battle.id(), ttl).exceptionally(_ -> null);
        this.openDialog(battle);
    }

    private void onChoiceError(Battle battle, BattleChoiceErrorPacket error) {
        ChoiceRequest pending = battle.pending();
        if (pending == null || pending.rqid() != error.rqid()) {
            // A choice that came too late (the turn timer answered for the player): only its item comes back
            this.refund(battle, error.rqid());
            return;
        }
        this.plugin.debug("Battle {}: choice refused ({})", battle.id(), error.message());
        battle.chosen(false);
        this.refund(battle, error.rqid());
        Player player = Bukkit.getPlayer(battle.playerId());
        if (player != null)
            PokeBattleMessages.CHOICE_REFUSED.message(player);
        this.openDialog(battle);
    }

    // ---- Choices ----

    /**
     * Shows the choice dialog for the pending request, if there is one to answer. Under the battle's monitor.
     */
    private void openDialog(Battle battle) {
        if (battle.state() != Battle.State.RUNNING || battle.chosen())
            return;
        Player player = Bukkit.getPlayer(battle.playerId());
        if (player == null)
            return;
        ChoiceView view = ChoiceView.of(battle, player.locale());
        if (view == null)
            return;
        if (view.doubles() != null)
            this.slotViews.put(battle.id(), view);
        this.withUi(battle.playerId(), (ui, viewer) -> ui.choose(viewer, view));
    }

    /**
     * Shows the view of a doubles slot still to choose for, unless another request came by the time it shows (that
     * one shows its own). Under the battle's monitor.
     */
    private void showSlot(Battle battle, ChoiceView view) {
        this.slotViews.put(battle.id(), view);
        this.withUi(battle.playerId(), (ui, viewer) -> {
            boolean current;
            synchronized (battle) {
                ChoiceRequest pending = battle.pending();
                current = battle.state() == Battle.State.RUNNING && !battle.chosen() && pending != null
                          && pending.rqid() == view.rqid();
            }
            if (current)
                ui.choose(viewer, view);
        });
    }

    /**
     * Shows the battle to the player (started, rejoined, {@code /combat}). Under the battle's monitor.
     */
    private void showField(Battle battle) {
        Player player = Bukkit.getPlayer(battle.playerId());
        if (player == null || battle.state() != Battle.State.RUNNING)
            return;
        FieldView field = FieldView.of(battle, player.locale());
        this.withUi(battle.playerId(), (ui, viewer) -> ui.open(viewer, field));
    }

    /**
     * Runs on the player's thread, in the order asked, with the UI they play with. Nothing if they are offline.
     */
    private void withUi(UUID playerId, BiConsumer<BattleUi, Player> action) {
        Player player = Bukkit.getPlayer(playerId);
        // Stopping: a settlement that ends while the server waits for it has nobody left to show it
        if (player == null || !this.plugin.isEnabled())
            return;
        BattleUi ui = this.plugin.ui();
        player.getScheduler().run(this.plugin, _ -> action.accept(ui, player), null);
    }

    /**
     * {@code /combat}: shows the pending choice again (closed by mistake, or rejoined).
     */
    public void reopen(Player player) {
        Battle battle = this.battleFor(player);
        if (battle == null) {
            PokeBattleMessages.NOT_IN_BATTLE.message(player);
            return;
        }
        synchronized (battle) {
            this.showField(battle);
            if (battle.pending() == null || battle.chosen() || !battle.pending().choosable()) {
                PokeBattleMessages.WAITING_FOR_TURN.message(player);
                return;
            }
            this.openDialog(battle);
        }
    }

    @Override
    public void choose(Player player, ChoiceView view, String choice) {
        Battle battle = this.battleFor(player);
        if (battle == null || !battle.id().equals(view.battleId())) {
            PokeBattleMessages.NOT_IN_BATTLE.message(player);
            return;
        }
        ChoiceView.Doubles doubles = view.doubles();
        if (doubles == null) {
            this.submit(player, battle, view.rqid(), choice);
            return;
        }
        // Doubles: each slot's pick is checked as it is made, then the next creature's choice; every slot's goes at
        // once, with the last one
        synchronized (battle) {
            ChoiceRequest request = this.answerable(player, battle, view.rqid());
            if (request == null)
                return;
            if (!SlotChoices.legal(request, doubles.slot(), choice, doubles.picks())) {
                PokeBattleMessages.CHOICE_UNAVAILABLE.message(player);
                this.showSlot(battle, view);
                return;
            }
            ChoiceView next = view.next(choice);
            if (next != null) {
                this.showSlot(battle, next);
                return;
            }
        }
        this.submit(player, battle, view.rqid(), view.choice(choice));
    }

    /**
     * A choice typed as a command, for the pending request.
     */
    public void choose(Player player, String choice) {
        Battle battle = this.battleFor(player);
        if (battle == null) {
            PokeBattleMessages.NOT_IN_BATTLE.message(player);
            return;
        }
        int rqid;
        ChoiceView view = null;
        synchronized (battle) {
            ChoiceRequest pending = battle.pending();
            rqid = pending == null ? -1 : pending.rqid();
            // Doubles: the pick is for the creature being chosen for, as a click would be
            if (pending != null && pending.doubles() && !battle.chosen()) {
                ChoiceView shown = this.slotViews.get(battle.id());
                view = shown != null && shown.rqid() == rqid ? shown : ChoiceView.of(battle, player.locale());
            }
        }
        if (view != null && view.doubles() != null) {
            this.choose(player, view, choice);
            return;
        }
        this.submit(player, battle, rqid, choice);
    }

    private void submit(Player player, Battle battle, int rqid, String choice) {
        String workerId;
        synchronized (battle) {
            ChoiceRequest request = this.answerable(player, battle, rqid);
            if (request == null)
                return;
            if (!legal(request, choice) || (choice.equals(RUN) && !battle.options().flee())) {
                PokeBattleMessages.CHOICE_UNAVAILABLE.message(player);
                this.openDialog(battle);
                return;
            }
            battle.chosen(true);
            workerId = battle.workerId();
        }
        this.send(player, battle, workerId, rqid, choice);
    }

    /**
     * A ball from the player's inventory. The capture is rolled here, where the formula, the ball rules and the
     * encounter's flags are, and the result goes to the worker with the choice (so a replay throws the same ball).
     * Must run on the player's thread (it takes the ball).
     */
    @Override
    public void throwBall(Player player, ChoiceView view, String ball) {
        Battle battle = this.battleFor(player);
        if (battle == null || !battle.id().equals(view.battleId())) {
            PokeBattleMessages.NOT_IN_BATTLE.message(player);
            return;
        }
        String workerId;
        String choice;
        synchronized (battle) {
            ChoiceRequest request = this.answerable(player, battle, view.rqid());
            if (request == null)
                return;
            CaptureAttempt attempt = request.canAct() && battle.options().capture() ? this.attempt(battle, ball) : null;
            if (attempt == null) {
                PokeBattleMessages.CHOICE_UNAVAILABLE.message(player);
                this.openDialog(battle);
                return;
            }
            // A catch with nowhere to go would be lost, and the ball with it
            if (!this.hasRoom(player)) {
                PokeBattleMessages.CAPTURE_NO_ROOM.message(player);
                this.openDialog(battle);
                return;
            }
            BattleBag.Taken taken = this.bag.take(player, ball);
            if (taken == null) {
                PokeBattleMessages.ITEM_MISSING.message(player);
                this.openDialog(battle);
                return;
            }
            CaptureResult result = this.guaranteed(player, battle)
                    ? GUARANTEED_CAPTURE
                    : PokeCoreAPI.get().capture().roll(attempt);
            this.plugin.debug("Battle {}: {} thrown, {} shakes, caught {}", battle.id(), ball, result.shakes(),
                    result.caught());
            // The engine knows the ball by its engine id, derived from the key here and mapped back by capturedBall
            String engineId = ItemKeys.showdownId(ball);
            battle.itemNames().put(engineId, taken.name());
            battle.itemKeys().put(engineId, ball);
            battle.spend(new Battle.Spent(view.rqid(), ball));
            battle.chosen(true);
            workerId = battle.workerId();
            choice = "ball " + engineId + " " + result.shakes() + " " + (result.caught() ? 1 : 0);
        }
        this.send(player, battle, workerId, view.rqid(), choice);
    }

    /**
     * A bag item from the player's inventory on the team member at {@code position}, with the effects its blueprint
     * fixes. Must run on the player's thread (it takes the item).
     */
    @Override
    public void useItem(Player player, ChoiceView view, String item, int position) {
        Battle battle = this.battleFor(player);
        if (battle == null || !battle.id().equals(view.battleId())) {
            PokeBattleMessages.NOT_IN_BATTLE.message(player);
            return;
        }
        String workerId;
        String choice;
        synchronized (battle) {
            ChoiceRequest request = this.answerable(player, battle, view.rqid());
            if (request == null)
                return;
            if (!request.canAct() || !battle.options().bag()
                    || request.team().stream().noneMatch(member -> member.position() == position)) {
                PokeBattleMessages.CHOICE_UNAVAILABLE.message(player);
                this.openDialog(battle);
                return;
            }
            BattleBag.Taken taken = this.bag.take(player, item);
            if (taken == null || taken.effects().isEmpty()) {
                if (taken != null)
                    this.bag.refund(player, item);
                PokeBattleMessages.ITEM_MISSING.message(player);
                this.openDialog(battle);
                return;
            }
            String engineId = ItemKeys.showdownId(item);
            battle.itemNames().put(engineId, taken.name());
            battle.itemKeys().put(engineId, item);
            battle.spend(new Battle.Spent(view.rqid(), item));
            battle.chosen(true);
            workerId = battle.workerId();
            choice = "item " + engineId + " " + position + " " + BattleBag.encode(taken.effects());
        }
        this.send(player, battle, workerId, view.rqid(), choice);
    }

    @Override
    public BattleBag bag() {
        return this.bag;
    }

    /**
     * Whether a caught creature has somewhere to go: a free party slot, else a free PC slot.
     */
    private boolean hasRoom(Player player) {
        TrainerStorage storage = PokeCoreAPI.get().trainers().cached(player.getUniqueId()).orElse(null);
        return storage != null && hasRoom(storage, PokeCoreAPI.get().trainers().limits(player));
    }

    static boolean hasRoom(TrainerStorage storage, StorageLimits limits) {
        Set<StoragePosition> pc = new HashSet<>();
        storage.pc().forEach((box, slots) -> slots.keySet().forEach(slot -> pc.add(StoragePosition.pc(box, slot))));
        return StorageSlots.firstFree(storage.party().keySet(), pc, limits, true).isPresent();
    }

    /**
     * The pending request, if the player can answer it now. Under the battle's monitor.
     */
    private @Nullable ChoiceRequest answerable(Player player, Battle battle, int rqid) {
        ChoiceRequest request = battle.pending();
        if (battle.state() != Battle.State.RUNNING || request == null || !request.choosable() || battle.chosen()
                || request.rqid() != rqid) {
            PokeBattleMessages.WAITING_FOR_TURN.message(player);
            return null;
        }
        return request;
    }

    /**
     * A throw at the opponent on the field. Its HP is only known as the percentage the player sees: close enough for
     * the formula (the error is under 1 % of the rate).
     */
    /**
     * Onboarding (GDD): a ball always catches while the player's Pokédex counts fewer caught species than
     * {@code capture.guaranteed-below}.
     */
    private boolean guaranteed(Player player, Battle battle) {
        PokeBattleConfiguration.Capture capture = this.plugin.configuration().captureOrDefault();
        int below = capture.guaranteedBelow();
        int maxLevel = capture.guaranteedMaxLevel();
        SignedRoll roll = battle.options().wildRoll();
        if (maxLevel > 0 && (roll == null || roll.creature().level() > maxLevel))
            return false;
        return below > 0 && PokeCoreAPI.get().pokedex().caughtSpecies(player.getUniqueId()) < below;
    }

    private @Nullable CaptureAttempt attempt(Battle battle, String ball) {
        SignedRoll roll = battle.options().wildRoll();
        if (PokeCoreAPI.get().capture().ball(ball).isEmpty() || roll == null)
            return null;
        String foeLabel = battle.tracker().active(Battle.FOE_SIDE);
        Battle.Foe foe = foeLabel == null ? null : battle.foe(foeLabel).orElse(null);
        Condition condition = foeLabel == null ? null : battle.tracker().condition(Battle.FOE_SIDE, foeLabel);
        if (foe == null || condition == null || condition.fainted())
            return null;

        SpeciesRegistry registry = PokeCoreAPI.get().species();
        GeneratedCreature creature = roll.creature();
        Species species = registry.findSpecies(creature.species()).orElse(null);
        if (species == null)
            return null;
        SpeciesVariant variant = species.variant(creature.form());
        int maxHp = StatCalculator.calculate(variant.baseStats(), creature.ivs(), StatSpread.ZERO, creature.level(),
                registry.findNature(creature.nature()).orElse(null)).hp();
        int currentHp = Math.clamp(Math.round(condition.percent() * maxHp / 100.0), 1, maxHp);

        String allyLabel = battle.tracker().active(Battle.PLAYER_SIDE);
        int throwerLevel = allyLabel == null ? 0 : battle.ally(allyLabel).map(ally -> ally.pokemon().level()).orElse(0);
        Set<String> types = variant.types()
                .stream()
                .map(type -> type.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return new CaptureAttempt(creature.species(), maxHp, currentHp,
                condition.status().isEmpty() ? null : condition.status(), ball,
                new CaptureContext(types, creature.level(), throwerLevel, Math.max(1, battle.tracker().turn()),
                        battle.options().captureFlags()));
    }

    private void send(Player player, Battle battle, @Nullable String workerId, int rqid, String choice) {
        this.withUi(battle.playerId(), (ui, viewer) -> ui.waiting(viewer));
        this.transport.choose(workerId, battle.id(), Battle.PLAYER_SIDE, rqid, choice).exceptionally(throwable -> {
            this.plugin.getSLF4JLogger().error("Battle {}: sending a choice failed", battle.id(), throwable);
            PokeBattleMessages.UNEXPECTED_ERROR.message(player);
            synchronized (battle) {
                ChoiceRequest pending = battle.pending();
                if (pending != null && pending.rqid() == rqid) {
                    battle.chosen(false);
                    this.refund(battle, rqid);
                    this.openDialog(battle);
                }
            }
            return null;
        });
    }

    /**
     * Gives back the item spent on the choice for {@code rqid}, which the battle refused. Under the battle's monitor.
     */
    private void refund(Battle battle, int rqid) {
        Battle.Spent spent = battle.unspend(rqid);
        if (spent == null)
            return;
        Player player = Bukkit.getPlayer(battle.playerId());
        if (player == null) {
            this.plugin.getSLF4JLogger().warn("Battle {}: {} could not be given back to {} (offline)", battle.id(),
                    spent.key(), battle.playerId());
            return;
        }
        Component name = battle.itemNames().get(ItemKeys.showdownId(spent.key()));
        player.getScheduler().run(this.plugin, _ -> {
            if (this.bag.refund(player, spent.key()))
                PokeBattleMessages.ITEM_REFUNDED.message(player, new PlaceholderContainer()
                        .registerDirect("item", name == null ? spent.key() : name));
            else
                this.plugin.getSLF4JLogger().warn("Battle {}: {} has no item stack any more, not given back",
                        battle.id(), spent.key());
        }, null);
    }

    /**
     * The choices the dialog and the commands send, checked against the request before they leave this server. Balls
     * and bag items are built here, not typed: they have their own checks.
     */
    static boolean legal(ChoiceRequest request, String choice) {
        // In doubles, one choice per slot: no ball, bag item nor flee (they take a whole side's turn on the worker)
        if (request.doubles())
            return SlotChoices.legal(request, choice);
        if (choice.equals(RUN))
            return request.canAct();
        String[] parts = choice.split(" ");
        if (parts.length != 2)
            return false;
        int index;
        try {
            index = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        return switch (parts[0]) {
            case "move" -> request.canUseMove(index);
            case "switch" -> request.canSwitchTo(index);
            default -> false;
        };
    }

    // ---- End ----

    @Override
    public boolean abort(UUID battleId, String reason) {
        Battle battle = this.battles.get(battleId);
        if (battle == null)
            return false;
        String workerId;
        synchronized (battle) {
            if (battle.state() == Battle.State.ENDED)
                return true;
            workerId = battle.workerId();
            if (workerId == null) {
                // Not taken by a worker yet: stopped as soon as one replies
                battle.abortReason(reason);
                return true;
            }
        }

        this.transport.end(workerId, battleId, "aborted", reason).exceptionally(throwable -> {
            this.plugin.getSLF4JLogger().warn("Battle {}: stop request failed", battleId, throwable);
            return null;
        });
        // A dead worker never answers: release the player anyway
        Bukkit.getAsyncScheduler().runDelayed(this.plugin, _ -> {
            if (this.battles.get(battleId) != battle)
                return;
            synchronized (battle) {
                if (battle.state() == Battle.State.ENDED)
                    return;
                battle.state(Battle.State.ENDED);
                // Its end may still come (the worker was only slow): kept a while to settle it
                this.aborted.put(battleId, battle);
            }
            this.plugin.getSLF4JLogger().warn("Battle {}: worker {} did not confirm the stop, releasing the player",
                    battleId, workerId);
            this.finish(battle, BattleOutcome.ABORTED, 0, false);
            Bukkit.getAsyncScheduler().runDelayed(this.plugin, _ -> this.aborted.remove(battleId, battle),
                    this.plugin.configuration().battle().endTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }, this.plugin.configuration().battle().endTimeout().toMillis(), TimeUnit.MILLISECONDS);
        return true;
    }

    private static BattleOutcome outcome(Battle battle, BattleEndedPacket ended) {
        return switch (ended.result()) {
            case Battle.PLAYER_SIDE -> battle.tracker().capture() != null ? BattleOutcome.CAPTURED : BattleOutcome.WON;
            case "tie" -> BattleOutcome.TIE;
            case "fled" -> BattleOutcome.FLED;
            case "aborted" -> BattleOutcome.ABORTED;
            default -> BattleOutcome.LOST;
        };
    }

    /**
     * Settles the battle if this server is the one to: the end may reach two of them (a late packet to the shard the
     * player left, the record read by the shard they joined). If Redis cannot say, it is settled here: the other path
     * reads Redis too. A claim is only a lease until the settlement is confirmed written (see
     * {@link BattleRecords.Claim}): a shard that stops in between leaves it to be settled again.
     */
    private void settle(Battle battle, BattleEndedPacket ended) {
        this.settle(battle, ended, 1);
    }

    private void settle(Battle battle, BattleEndedPacket ended, int attempt) {
        PokeBattleConfiguration.Recovery recovery = this.plugin.configuration().recoveryOrDefault();
        this.records.claimSettlement(battle.id(), this.shardId, recovery.settleLease())
                .exceptionally(throwable -> {
                    this.plugin.getSLF4JLogger().warn("Battle {}: settlement claim failed, settling here", battle.id(),
                            throwable);
                    // Maybe written already: as a claim taken again
                    return BattleRecords.Claim.RETAKEN;
                })
                .thenAccept(claim -> {
                    switch (claim) {
                        case CLAIMED -> this.settleClaimed(battle, ended, false, attempt);
                        case RETAKEN -> this.settleClaimed(battle, ended, true, attempt);
                        case SETTLED -> {
                            this.plugin.debug("Battle {} was settled by another server", battle.id());
                            this.finish(battle, outcome(battle, ended), ended.turns(), false);
                        }
                        case BUSY -> {
                            // Another server is writing it; if it stopped meanwhile, its claim is taken once over
                            this.plugin.debug("Battle {} is being settled by another server", battle.id());
                            Bukkit.getAsyncScheduler().runDelayed(this.plugin, _ -> this.settle(battle, ended, attempt),
                                    recovery.settleLease().toMillis(), TimeUnit.MILLISECONDS);
                        }
                    }
                });
    }

    /**
     * @param retaken an earlier claim was never confirmed: what it wrote, if it wrote it, is not written again
     */
    private void settleClaimed(Battle battle, BattleEndedPacket ended, boolean retaken, int attempt) {
        BattleTracker.Capture capture = battle.tracker().capture();
        BattleOutcome outcome = outcome(battle, ended);
        if (battle.options().foughtByStandIns()) {
            this.settleStandIns(battle, outcome, ended.turns());
            return;
        }
        BattleTeamState team = ended.teams()
                .stream()
                .filter(state -> state.slot().equals(Battle.PLAYER_SIDE))
                .findFirst()
                .orElse(null);

        // The condition is kept whatever the outcome (leaving a battle never heals); rewards only for a win
        List<BattleCondition> conditions = new ArrayList<>();
        Map<String, RewardCalculator.Ally> allies = new LinkedHashMap<>();
        for (int i = 0; i < battle.allies().size(); i++) {
            Battle.Ally ally = battle.allies().get(i);
            BattleMemberState state = team != null && i < team.members().size() ? team.members().get(i) : null;
            if (state != null)
                conditions.add(new BattleCondition(ally.pokemon().id(), ally.pokemon().version(), state.hp(),
                        state.status().isEmpty() ? null : state.status(), state.pp(),
                        moveIds(ally.pokemon(), state.pp()), state.itemConsumed() ? ally.pokemon().heldItem() : null));
            allies.put(ally.label(), new RewardCalculator.Ally(ally.pokemon().id(), ally.pokemon().version(),
                    ally.pokemon().level(), state != null && state.hp() > 0));
        }

        // Rewards for a win; for a catch too (modern rule, configurable): the caught opponent counts as defeated
        List<ExperienceGain> gains = List.of();
        boolean rewarded = outcome == BattleOutcome.WON
                || (outcome == BattleOutcome.CAPTURED && this.plugin.configuration().rewards().experienceOnCapture());
        if (rewarded) {
            SpeciesRegistry registry = PokeCoreAPI.get().species();
            Map<String, RewardCalculator.Foe> foes = new LinkedHashMap<>();
            for (Battle.Foe foe : battle.foes()) {
                Species species = registry.findSpecies(foe.species()).orElse(null);
                if (species != null)
                    foes.put(foe.label(), new RewardCalculator.Foe(species.baseExperience(), foe.level(),
                            species.evYield()));
            }
            List<BattleTracker.Defeat> defeats = new ArrayList<>(battle.tracker().defeats());
            if (capture != null)
                defeats.add(new BattleTracker.Defeat(capture.label(), capture.participants()));
            RewardCalculator.Rules rules = this.plugin.configuration().rewards().rules();
            // A trainer's win is worth more (the request's multiplier, on top of the server's)
            rules = new RewardCalculator.Rules(rules.experienceMultiplier() * battle.options().experience(),
                    rules.effortValues(), rules.friendship());
            gains = RewardCalculator.gains(defeats, allies, foes, rules);
        }

        List<ExperienceGain> granted = gains;
        CompletableFuture<@Nullable CreationResult> caught = outcome == BattleOutcome.CAPTURED
                ? this.redeem(battle, capture, retaken)
                : CompletableFuture.completedFuture(null);
        CompletableFuture<Void> settlement = caught.thenCompose(creation -> {
                    // The caught creature keeps the condition the battle left it in, like the team
                    List<BattleCondition> all = new ArrayList<>(conditions);
                    BattleCondition captured = this.capturedCondition(battle, capture, creation, ended);
                    if (captured != null)
                        all.add(captured);
                    // Taken again: a stale write means the first claim wrote it, it must not be applied on top
                    return this.settle(battle.playerId(), all, granted, !retaken).thenCompose(result -> {
                        if (result.isEmpty() && retaken)
                            this.plugin.getSLF4JLogger().warn("Battle {}: settlement taken again was stale, taken as"
                                    + " written by the first claim", battle.id());
                        return this.records.confirmSettlement(battle.id(), this.shardId)
                                .exceptionally(throwable -> {
                                    this.plugin.getSLF4JLogger().warn("Battle {}: settlement written but not"
                                            + " confirmed in Redis", battle.id(), throwable);
                                    return null;
                                })
                                .thenRun(() -> {
                                    boolean settled = result.isPresent() || retaken;
                                    this.report(battle, outcome, creation, granted,
                                            result.orElse(retaken ? List.of() : null));
                                    this.finish(battle, outcome, ended.turns(), settled);
                                });
                    });
                })
                .exceptionally(throwable -> {
                    int attempts = this.plugin.configuration().recoveryOrDefault().settleAttempts();
                    if (attempt < attempts && this.plugin.isEnabled()) {
                        this.plugin.getSLF4JLogger().warn("Battle {}: settlement failed (attempt {}/{}), trying again",
                                battle.id(), attempt, attempts, throwable);
                        Bukkit.getAsyncScheduler().runDelayed(this.plugin, _ -> this.settle(battle, ended,
                                        attempt + 1),
                                this.plugin.configuration().recoveryOrDefault().checkInterval().toMillis(),
                                TimeUnit.MILLISECONDS);
                        return null;
                    }
                    // Not confirmed: its claim is taken again if the battle comes back (its player rejoins in time)
                    this.plugin.getSLF4JLogger().error("Battle {}: settlement failed", battle.id(), throwable);
                    this.report(battle, outcome, null, List.of(), null);
                    this.finish(battle, outcome, ended.turns(), false);
                    return null;
                });
        this.settling.add(settlement);
        settlement.whenComplete((_, _) -> this.settling.remove(settlement));
    }

    /**
     * Stores the caught creature: the signed roll it appeared with, redeemed once. A refusal (storage full, invalid
     * or already redeemed roll) still settles the rest of the battle; a database error fails the settlement, which is
     * tried again.
     *
     * @param retaken an earlier claim may have stored it: already redeemed is then caught
     */
    private CompletableFuture<@Nullable CreationResult> redeem(Battle battle, BattleTracker.@Nullable Capture capture,
                                                               boolean retaken) {
        SignedRoll roll = battle.options().wildRoll();
        if (capture == null || roll == null)
            return CompletableFuture.completedFuture(null);
        String ball = this.capturedBall(battle, capture.ball());
        return PokeCoreAPI.get()
                .creation()
                .redeem(battle.playerId(), roll, CreationOptions.capture(ball, battle.options().metAt(),
                        battle.id().toString()))
                .thenApply(result -> {
                    if (retaken && result.status() == StorageResult.Status.DUPLICATE) {
                        this.plugin.getSLF4JLogger().info("Battle {}: the caught creature was stored by the first"
                                + " claim", battle.id());
                        return new CreationResult(StorageResult.Status.SUCCESS, null, null);
                    }
                    if (!result.succeeded()) {
                        // Checked before the throw: storage filled meanwhile (another server), or a refused roll
                        this.plugin.getSLF4JLogger().warn("Battle {}: the caught creature of {} was not stored ({})",
                                battle.id(), battle.playerId(), result.status());
                        this.refundBall(battle, ball);
                    }
                    return result;
                });
    }

    /**
     * The global key of the ball the engine reports a catch with (its engine id): the key this shard sent it as, else
     * the only ball of {@code balls.yml} (then the only AstralItems item) with that engine id, e.g. after the battle
     * moved shards. Falls back to the engine id itself, logged, rather than losing the catch.
     */
    private String capturedBall(Battle battle, String engineId) {
        String sent = battle.itemKeys().get(engineId);
        if (sent != null)
            return sent;
        List<String> balls = PokeCoreAPI.get()
                .capture()
                .balls()
                .stream()
                .map(BallDefinition::id)
                .toList();
        return PokeCoreAPI.get().items().fromShowdownId(engineId, balls).orElseGet(() -> {
            this.plugin.getSLF4JLogger().warn("Battle {}: caught with ball {}, which matches no single item key",
                    battle.id(), engineId);
            return engineId;
        });
    }

    /**
     * Gives back the ball of a catch that could not be kept: at least the throw is not lost.
     */
    private void refundBall(Battle battle, String ball) {
        Player player = Bukkit.getPlayer(battle.playerId());
        if (player == null || !this.plugin.isEnabled()) {
            this.plugin.getSLF4JLogger().warn("Battle {}: {} could not be given back to {} (offline)", battle.id(),
                    ball, battle.playerId());
            return;
        }
        Component name = battle.itemNames().get(ItemKeys.showdownId(ball));
        player.getScheduler().run(this.plugin, _ -> {
            if (this.bag.refund(player, ball))
                PokeBattleMessages.ITEM_REFUNDED.message(player, new PlaceholderContainer()
                        .registerDirect("item", name == null ? ball : name));
            else
                this.plugin.getSLF4JLogger().warn("Battle {}: {} has no item stack any more, not given back",
                        battle.id(), ball);
        }, null);
    }

    private @Nullable BattleCondition capturedCondition(Battle battle, BattleTracker.@Nullable Capture capture,
                                                        @Nullable CreationResult creation, BattleEndedPacket ended) {
        if (capture == null || creation == null || !creation.succeeded() || creation.pokemon() == null)
            return null;
        int index = -1;
        for (int i = 0; i < battle.foes().size(); i++)
            if (battle.foes().get(i).label().equals(capture.label()))
                index = i;
        int foeIndex = index;
        BattleMemberState state = ended.teams()
                .stream()
                .filter(team -> team.slot().equals(Battle.FOE_SIDE))
                .findFirst()
                .filter(team -> foeIndex >= 0 && foeIndex < team.members().size())
                .map(team -> team.members().get(foeIndex))
                .orElse(null);
        if (state == null)
            return null;
        Pokemon stored = creation.pokemon();
        return new BattleCondition(stored.id(), stored.version(), state.hp(),
                state.status().isEmpty() ? null : state.status(), state.pp(), moveIds(stored, state.pp()),
                state.itemConsumed() ? stored.heldItem() : null);
    }

    /**
     * The move ids the PP were reported for (the moves the creature started the battle with), so PokeCore matches PP by
     * move rather than by slot. Empty (PP by order) when the counts disagree.
     */
    private static List<String> moveIds(Pokemon pokemon, List<Integer> pp) {
        if (pokemon.moves().size() != pp.size())
            return List.of();
        return pokemon.moves().stream().map(MoveSlot::move).toList();
    }

    /**
     * Writes the outcome. If a creature changed during the battle (another server, an admin), the outcome is applied
     * again on top of the current creatures, once: the battle's result wins over the change.
     */
    private CompletableFuture<Optional<List<Progression>>> settle(UUID owner, List<BattleCondition> conditions,
                                                                  List<ExperienceGain> gains, boolean retry) {
        return PokeCoreAPI.get()
                .progression()
                .settle(owner, conditions, gains)
                .thenCompose(result -> {
                    if (result.isPresent() || !retry)
                        return CompletableFuture.completedFuture(result);
                    this.plugin.getSLF4JLogger().warn("Battle settlement for {} was stale, applying it again", owner);
                    return PokeCoreAPI.get().trainers().load(owner).thenCompose(storage -> this.settle(owner,
                            conditions.stream()
                                    .filter(condition -> storage.find(condition.pokemonId()).isPresent())
                                    .map(condition -> new BattleCondition(condition.pokemonId(),
                                            storage.find(condition.pokemonId()).orElseThrow().version(),
                                            condition.hp(), condition.status(), condition.pp(), condition.moves(),
                                            condition.consumedItem()))
                                    .toList(),
                            gains.stream()
                                    .filter(gain -> storage.find(gain.pokemonId()).isPresent())
                                    .map(gain -> new ExperienceGain(gain.pokemonId(),
                                            storage.find(gain.pokemonId()).orElseThrow().version(),
                                            gain.experience(), gain.evs(), gain.friendship()))
                                    .toList(),
                            false));
                });
    }

    /**
     * A battle fought by stand-ins writes nothing (no condition, no reward, no capture): only its outcome is told.
     */
    private void settleStandIns(Battle battle, BattleOutcome outcome, int turns) {
        this.records.confirmSettlement(battle.id(), this.shardId)
                .exceptionally(throwable -> {
                    this.plugin.getSLF4JLogger().warn("Battle {}: stand-in settlement not confirmed in Redis",
                            battle.id(), throwable);
                    return null;
                })
                .thenRun(() -> {
                    this.report(battle, outcome, null, List.of(), List.of());
                    this.finish(battle, outcome, turns, true);
                });
    }

    /**
     * @param progressions null when nothing was written
     */
    private void report(Battle battle, BattleOutcome outcome, @Nullable CreationResult creation,
                        List<ExperienceGain> gains, @Nullable List<Progression> progressions) {
        // After the last beats: the chat must not tell the end before the player has seen it
        this.withUi(battle.playerId(), (ui, viewer) -> ui.then(viewer,
                () -> this.tell(viewer, battle, outcome, creation, gains, progressions)));
    }

    private void tell(Player player, Battle battle, BattleOutcome outcome, @Nullable CreationResult creation,
                      List<ExperienceGain> gains, @Nullable List<Progression> progressions) {
        PlaceholderContainer trainer = new PlaceholderContainer().registerDirect("trainer",
                battle.options().trainer() == null ? "" : battle.options().trainer());
        switch (outcome) {
            case WON -> {
                if (battle.options().trainer() == null)
                    PokeBattleMessages.BATTLE_WON.message(player);
                else
                    PokeBattleMessages.BATTLE_WON_TRAINER.message(player, trainer);
            }
            case CAPTURED -> this.reportCapture(player, battle, creation);
            case LOST -> {
                if (battle.options().trainer() == null)
                    PokeBattleMessages.BATTLE_LOST.message(player);
                else
                    PokeBattleMessages.BATTLE_LOST_TRAINER.message(player, trainer);
            }
            case TIE -> PokeBattleMessages.BATTLE_TIE.message(player);
            case FLED -> PokeBattleMessages.BATTLE_FLED.message(player);
            case ABORTED -> PokeBattleMessages.BATTLE_ABORTED.message(player);
        }
        if (progressions == null) {
            PokeBattleMessages.SETTLEMENT_FAILED.message(player);
            return;
        }

        Map<UUID, Integer> experience = new LinkedHashMap<>();
        gains.forEach(gain -> experience.put(gain.pokemonId(), gain.experience()));
        for (Progression progression : progressions) {
            Pokemon after = progression.after();
            String name = after.nickname() != null && !after.nickname().isBlank()
                    ? after.nickname()
                    : Names.text(player.locale(), "species", after.species());
            Integer gained = experience.get(after.id());
            if (gained != null)
                PokeBattleMessages.EXPERIENCE_GAINED.message(player, new PlaceholderContainer()
                        .registerDirect("pokemon", name)
                        .registerDirect("experience", gained));
            if (progression.leveledUp())
                PokeBattleMessages.LEVEL_UP.message(player, new PlaceholderContainer()
                        .registerDirect("pokemon", name)
                        .registerDirect("level", after.level()));
            for (String move : progression.learned())
                PokeBattleMessages.MOVE_LEARNED.message(player, new PlaceholderContainer()
                        .registerDirect("pokemon", name)
                        .registerDirect("move", Names.text(player.locale(), "move", move)));
            for (String move : progression.pending())
                PokeBattleMessages.MOVE_PENDING.message(player, new PlaceholderContainer()
                        .registerDirect("pokemon", name)
                        .registerDirect("move", Names.text(player.locale(), "move", move)));
        }
    }

    private void reportCapture(Player player, Battle battle, @Nullable CreationResult creation) {
        PlaceholderContainer placeholders = new PlaceholderContainer().registerDirect("pokemon",
                battle.foes().isEmpty() ? "" : Names.foe(player.locale(), battle.foes().getFirst()));
        if (creation == null || !creation.succeeded()) {
            (creation != null && creation.status() == StorageResult.Status.STORAGE_FULL
                    ? PokeBattleMessages.CAPTURE_STORAGE_FULL
                    : PokeBattleMessages.CAPTURE_FAILED).message(player, placeholders);
            return;
        }
        StoragePosition position = creation.position();
        if (position == null || position.party())
            PokeBattleMessages.BATTLE_CAPTURED.message(player, placeholders);
        else
            PokeBattleMessages.BATTLE_CAPTURED_PC.message(player, placeholders.registerDirect("box", position.box() + 1));
    }

    private void finish(Battle battle, BattleOutcome outcome, int turns, boolean settled) {
        this.forget(battle);
        this.closeUi(battle);
        this.records.deleteContext(battle.id()).exceptionally(_ -> null);
        this.locks.release(battle.playerId(), battle.id()).exceptionally(throwable -> {
            this.plugin.getSLF4JLogger().warn("Battle {}: lock release failed", battle.id(), throwable);
            return false;
        });
        Bukkit.getPluginManager().callEvent(new BattleEndEvent(battle.id(), battle.playerId(), battle.format(), outcome,
                turns, settled, battle.options().tag()));
    }

    /**
     * Drops a battle that never ran (no worker, refused, failed): nothing to settle.
     */
    private void discard(Battle battle) {
        this.forget(battle);
        this.closeUi(battle);
        this.locks.release(battle.playerId(), battle.id()).exceptionally(_ -> false);
        this.records.deleteContext(battle.id()).exceptionally(_ -> null);
    }

    /**
     * Stops showing a battle that goes on elsewhere (another shard took it over): its lock and records stay.
     */
    private void letGo(Battle battle) {
        this.forget(battle);
        this.closeUi(battle);
    }

    /**
     * Closes the battle's UI for its player, or, if they are gone, drops what the UI kept for it (its stage, its
     * creatures): nothing else would.
     */
    private void closeUi(Battle battle) {
        if (!this.plugin.isEnabled())
            return;
        BattleUi ui = this.plugin.ui();
        UUID battleId = battle.id();
        Player player = Bukkit.getPlayer(battle.playerId());
        if (player != null && player.getScheduler().run(this.plugin, _ -> ui.close(player, battleId),
                () -> ui.release(battleId)) != null)
            return;
        Bukkit.getGlobalRegionScheduler().run(this.plugin, _ -> ui.release(battleId));
    }

    private void forget(Battle battle) {
        this.slotViews.remove(battle.id());
        this.battles.remove(battle.id(), battle);
        this.byPlayer.remove(battle.playerId(), battle.id());
    }

    // ---- Recovery ----

    /**
     * Checks the battles this server shows against their records (run every {@code recovery.check-interval}): a dead
     * worker's battle is recovered, an end this server missed is settled, a battle another server took over is let go.
     */
    public void checkBattles() {
        List<Battle> checked = this.battles.values()
                .stream()
                .filter(battle -> battle.state() == Battle.State.RUNNING && !battle.recovering()
                        && battle.workerId() != null)
                .toList();
        if (checked.isEmpty())
            return;
        // One round trip for all of them
        this.records.states(checked.stream().map(Battle::id).toList())
                .thenAccept(states -> {
                    for (Battle battle : checked) {
                        BattleRecords.State state = states.get(battle.id());
                        if (state != null)
                            this.check(battle, state);
                    }
                })
                .exceptionally(throwable -> {
                    this.plugin.debug("Battle record check failed ({})", throwable.getMessage());
                    return null;
                });
    }

    private void check(Battle battle, BattleRecords.State state) {
        switch (state.status()) {
            case ALIVE -> {
                if (state.shardId() != null && !state.shardId().equals(this.shardId)) {
                    this.plugin.debug("Battle {} moved to {}", battle.id(), state.shardId());
                    this.letGo(battle);
                }
            }
            case DEAD -> this.recover(battle);
            case ENDED -> this.settleFromRecord(battle);
            case MISSING -> this.lost(battle);
        }
    }

    /**
     * Asks the start queue for a worker to take the battle over. Until one does, the player cannot choose; a choice
     * already sent may be lost with the dead worker, the new one asks again.
     */
    private void recover(Battle battle) {
        synchronized (battle) {
            if (battle.state() != Battle.State.RUNNING || battle.recovering())
                return;
            battle.recovering(true);
            battle.restartSequence();
            battle.pending(null);
            battle.chosen(false);
        }
        this.plugin.getSLF4JLogger().warn("Battle {}: worker {} is gone, asking another one to take it over",
                battle.id(), battle.workerId());
        Player player = Bukkit.getPlayer(battle.playerId());
        if (player != null)
            PokeBattleMessages.BATTLE_RECOVERING.message(player);

        PokeBattleConfiguration.Start start = this.plugin.configuration().start();
        this.transport.recover(battle.id(), this.shardId, start.timeout(), start.attempts())
                .thenAccept(reply -> this.onRecoverReply(battle, reply))
                .exceptionally(throwable -> {
                    this.plugin.getSLF4JLogger().error("Battle {}: recovery failed", battle.id(), throwable);
                    battle.recovering(false);
                    return null;
                });
    }

    private void onRecoverReply(Battle battle, @Nullable BattleStartReplyPacket reply) {
        if (reply == null) {
            // Checked again on the next round
            this.plugin.getSLF4JLogger().warn("Battle {}: no worker took it over yet", battle.id());
            battle.recovering(false);
            return;
        }
        if (reply.accepted()) {
            String abortReason;
            synchronized (battle) {
                battle.workerId(reply.workerId());
                battle.recovering(false);
                abortReason = battle.abortReason();
            }
            this.plugin.getSLF4JLogger().info("Battle {} recovered on worker {}", battle.id(), reply.workerId());
            Player player = Bukkit.getPlayer(battle.playerId());
            if (player != null)
                PokeBattleMessages.BATTLE_RESUMED.message(player);
            if (abortReason != null)
                this.abort(battle.id(), abortReason);
            return;
        }
        switch (reply.reason()) {
            case "ended" -> this.settleFromRecord(battle);
            // Its worker beats after all (a late heartbeat): it resends the battle from 0, as this view expects now
            case "alive" -> this.records.state(battle.id()).thenAccept(state -> {
                synchronized (battle) {
                    battle.workerId(state.workerId());
                    battle.recovering(false);
                }
                if (state.workerId() != null)
                    this.transport.attach(state.workerId(), battle.id(), this.shardId);
            });
            default -> {
                this.plugin.getSLF4JLogger().error("Battle {} cannot be recovered: {}", battle.id(), reply.reason());
                this.lost(battle);
            }
        }
    }

    /**
     * Settles the end the worker left in Redis, which this server did not receive.
     */
    private void settleFromRecord(Battle battle) {
        this.records.ended(battle.id())
                .thenAccept(ended -> {
                    if (ended.isEmpty()) {
                        this.lost(battle);
                        return;
                    }
                    synchronized (battle) {
                        if (battle.state() == Battle.State.ENDED)
                            return;
                        battle.state(Battle.State.ENDED);
                        this.cancelDisconnect(battle);
                    }
                    this.settle(battle, ended.get());
                })
                .exceptionally(throwable -> {
                    this.plugin.getSLF4JLogger().error("Battle {}: reading its end failed", battle.id(), throwable);
                    return null;
                });
    }

    /**
     * The battle is gone with no end to settle (its records expired, or no worker can replay it): the player is
     * released and the creatures keep their state from before it.
     */
    private void lost(Battle battle) {
        synchronized (battle) {
            if (battle.state() == Battle.State.ENDED)
                return;
            battle.state(Battle.State.ENDED);
            this.cancelDisconnect(battle);
        }
        this.plugin.getSLF4JLogger().warn("Battle {} is lost, releasing {}", battle.id(), battle.playerId());
        this.report(battle, BattleOutcome.ABORTED, null, List.of(), List.of());
        this.finish(battle, BattleOutcome.ABORTED, 0, false);
    }

    // ---- Connection ----

    /**
     * A player in battle left: the battle waits for them, then is lost with no penalty. Unless it went on elsewhere:
     * another server took it over meanwhile (they changed server).
     */
    public void onQuit(UUID playerId) {
        UUID battleId = this.byPlayer.get(playerId);
        Battle battle = battleId == null ? null : this.battles.get(battleId);
        if (battle == null)
            return;
        Duration grace = this.plugin.configuration().battle().disconnectGrace();
        synchronized (battle) {
            this.cancelDisconnect(battle);
            battle.disconnectTask(Bukkit.getAsyncScheduler().runDelayed(this.plugin, _ -> this.records.state(battleId)
                    .exceptionally(_ -> new BattleRecords.State(BattleRecords.Status.MISSING, null, null))
                    .thenAccept(state -> {
                        if (state.shardId() != null && !state.shardId().equals(this.shardId))
                            this.letGo(battle);
                        else
                            this.abort(battleId, "disconnected");
                    }), grace.toMillis(), TimeUnit.MILLISECONDS));
        }
    }

    public void onJoin(Player player) {
        Battle battle = this.battleFor(player);
        if (battle == null) {
            this.adopt(player);
            return;
        }
        synchronized (battle) {
            this.cancelDisconnect(battle);
            this.showField(battle);
            this.openDialog(battle);
        }
        this.resumed(battle, player);
    }

    /**
     * Takes over the battle the player is in elsewhere, if any: rebuilt from its context in Redis, then the worker
     * resends it here (or another one replays it, or its end is settled here).
     */
    private void adopt(Player player) {
        UUID playerId = player.getUniqueId();
        this.locks.current(playerId)
                .thenCompose(current -> {
                    if (current.isEmpty())
                        return CompletableFuture.completedFuture(null);
                    UUID battleId = current.get();
                    return this.records.state(battleId).thenCombine(this.records.context(battleId),
                            (state, context) -> {
                                this.adopt(player, battleId, state, context.orElse(null));
                                return null;
                            });
                })
                .exceptionally(throwable -> {
                    this.plugin.getSLF4JLogger().error("Taking over the battle of {} failed", player.getName(),
                            throwable);
                    return null;
                });
    }

    private void adopt(Player player, UUID battleId, BattleRecords.State state, @Nullable BattleContext context) {
        UUID playerId = player.getUniqueId();
        Battle battle = context == null || state.status() == BattleRecords.Status.MISSING ? null : this.rebuild(context);
        if (battle == null) {
            this.plugin.getSLF4JLogger().warn("Battle {} of {} cannot be taken over ({}), releasing them", battleId,
                    player.getName(), context == null ? "no context" : state.status());
            if (state.workerId() != null && state.status() == BattleRecords.Status.ALIVE)
                this.transport.end(state.workerId(), battleId, "aborted", "no context").exceptionally(_ -> null);
            this.locks.release(playerId, battleId).exceptionally(_ -> false);
            this.records.deleteContext(battleId).exceptionally(_ -> null);
            return;
        }
        if (this.byPlayer.putIfAbsent(playerId, battleId) != null)
            return;
        battle.state(Battle.State.RUNNING);
        battle.workerId(state.workerId());
        battle.adopted(true);
        this.battles.put(battleId, battle);
        if (context.staging() != null)
            this.restage(player, battleId, context.staging());
        this.locks.refresh(playerId, battleId, this.plugin.configuration().battle().lockTtl()).exceptionally(_ -> false);
        this.plugin.getSLF4JLogger().info("Taking over battle {} of {} ({})", battleId, player.getName(),
                state.status());

        switch (state.status()) {
            case ALIVE -> {
                PokeBattleMessages.BATTLE_RESUMED.message(player);
                this.records.moveTo(battleId, this.shardId)
                        .thenCompose(_ -> this.transport.attach(state.workerId(), battleId, this.shardId))
                        .exceptionally(throwable -> {
                            this.plugin.getSLF4JLogger().error("Battle {}: attach failed", battleId, throwable);
                            return null;
                        });
            }
            case DEAD -> this.recover(battle);
            case ENDED -> this.settleFromRecord(battle);
            case MISSING -> {
            }
        }
        if (state.status() == BattleRecords.Status.ALIVE || state.status() == BattleRecords.Status.DEAD)
            this.resumed(battle, player);
    }

    /**
     * Tells the other plugins the player got their battle back, for them to put back what they staged on it.
     */
    private void resumed(Battle battle, Player player) {
        BattleStage stage = this.plugin.stages().placed(battle.id()) ? this.plugin.stages().find(battle.id()) : null;
        Bukkit.getPluginManager().callEvent(new BattleResumeEvent(battle.id(), player, battle.format(),
                battle.options().tag(), stage == null ? null : stage.origin()));
    }

    /**
     * Lays a battle taken over where its first shard staged it (an arena, a gym's marks), when this server has that
     * world: the same arena server, its instance still open. Elsewhere it is laid in front of the player, as any
     * battle that was not staged.
     */
    private void restage(Player player, UUID battleId, BattleContext.Staging staging) {
        World world = Bukkit.getWorld(staging.world());
        if (world == null) {
            this.plugin.getSLF4JLogger().warn("Battle {}: its stage world {} is not on this server, laid in front of "
                    + "{}", battleId, staging.world(), player.getName());
            return;
        }
        this.plugin.stages().place(battleId, staging.stage(world), staging.spots(world), this.plugin.configuration());
        if (!world.equals(player.getWorld()))
            this.plugin.getSLF4JLogger().info("Battle {}: staged again in {}, {} is in {} for now", battleId,
                    world.getName(), player.getName(), player.getWorld().getName());
    }

    /**
     * The battle as its first shard built it; null if one of the player's stored creatures is no longer in their
     * storage.
     */
    private @Nullable Battle rebuild(BattleContext context) {
        List<Battle.Ally> allies = new ArrayList<>();
        if (context.standIns() != null) {
            // Stand-ins are in no storage: the context kept them whole
            for (Pokemon pokemon : context.standIns())
                allies.add(new Battle.Ally(Battle.label(allies.size()), pokemon));
        } else {
            TrainerStorage storage = PokeCoreAPI.get().trainers().cached(context.playerId()).orElse(null);
            if (storage == null)
                return null;
            for (UUID pokemonId : context.allies()) {
                Pokemon pokemon = storage.find(pokemonId).orElse(null);
                if (pokemon == null)
                    return null;
                allies.add(new Battle.Ally(Battle.label(allies.size()), pokemon));
            }
        }
        List<Battle.Foe> foes = new ArrayList<>();
        // Labelled again by position: a context written before trainers has no labels
        for (Battle.Foe foe : context.foes())
            foes.add(foe.labelled(Battle.label(foes.size())));
        return new Battle(context.battleId(), context.format(), context.playerId(), allies, foes, context.options());
    }

    private void cancelDisconnect(Battle battle) {
        if (battle.disconnectTask() != null) {
            battle.disconnectTask().cancel();
            battle.disconnectTask(null);
        }
    }

    /**
     * Server stopping: the battles go on without it. Their players get them back on the server they join next
     * (re-attach); one who does not come back is aborted by the worker after a few turns ({@code ABSENT_TURNS}).
     */
    public void shutdown() {
        for (Battle battle : List.copyOf(this.battles.values()))
            this.forget(battle);
        // Settlements being written end before PokeCore's database closes (PokeCore is disabled after this plugin).
        // One that does not in time stays unconfirmed: it is settled again when its player comes back
        CompletableFuture<?>[] pending = this.settling.toArray(CompletableFuture[]::new);
        if (pending.length == 0)
            return;
        Duration wait = this.plugin.configuration().recoveryOrDefault().settleLease();
        this.plugin.getSLF4JLogger().info("Waiting for {} battle settlement(s)", pending.length);
        try {
            CompletableFuture.allOf(pending).get(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            this.plugin.getSLF4JLogger().warn("{} battle settlement(s) not written in {}, left to be settled again",
                    this.settling.size(), wait);
        } catch (ExecutionException e) {
            // Each logs its own failure
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
