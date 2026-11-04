package com.astralrealms.poke.battle.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.core.packet.Packet;
import com.astralrealms.core.placeholder.container.PlaceholderContainer;
import com.astralrealms.poke.battle.PokeBattle;
import com.astralrealms.poke.battle.api.event.BattleEndEvent;
import com.astralrealms.poke.battle.api.event.BattleStartEvent;
import com.astralrealms.poke.battle.api.model.BattleOutcome;
import com.astralrealms.poke.battle.api.model.BattleRequest;
import com.astralrealms.poke.battle.api.model.BattleStartResult;
import com.astralrealms.poke.battle.api.service.BattleService;
import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;
import com.astralrealms.poke.battle.configuration.PokeBattleMessages;
import com.astralrealms.poke.battle.model.Battle;
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
import com.astralrealms.poke.battle.protocol.ProtocolLine;
import com.astralrealms.poke.battle.render.BattleLogRenderer;
import com.astralrealms.poke.battle.ui.ChoiceDialogs;
import com.astralrealms.poke.battle.ui.ChoiceView;
import com.astralrealms.poke.core.PokeCoreAPI;
import com.astralrealms.poke.core.mechanics.PokemonStats;
import com.astralrealms.poke.core.mechanics.progression.BattleCondition;
import com.astralrealms.poke.core.mechanics.progression.ExperienceGain;
import com.astralrealms.poke.core.mechanics.progression.Progression;
import com.astralrealms.poke.core.model.MoveSlot;
import com.astralrealms.poke.core.model.Pokemon;
import com.astralrealms.poke.core.model.storage.TrainerStorage;
import com.astralrealms.poke.core.species.Species;
import com.astralrealms.poke.core.species.SpeciesRegistry;
import com.astralrealms.poke.core.team.BattleSets;
import com.astralrealms.poke.core.team.PackedSet;
import com.astralrealms.poke.core.team.PackedTeam;
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
 *     then the lock is released.</li>
 * </ul>
 * Packets arrive on messaging threads; a battle's state is only touched under its monitor, and the player only on
 * its own thread.
 */
public class BattleServiceImpl implements BattleService, ChoiceDialogs.Handler {

    private static final int MAX_TEAM = 6;
    private static final String WILD_SIDE_NAME = "wild";
    private static final String WILD_AI = "wild";

    private final PokeBattle plugin;
    private final BattleTransport transport;
    private final BattleLocks locks;
    private final String shardId;
    private final Map<UUID, Battle> battles = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> byPlayer = new ConcurrentHashMap<>();

    public BattleServiceImpl(PokeBattle plugin, BattleTransport transport, BattleLocks locks, String shardId) {
        this.plugin = plugin;
        this.transport = transport;
        this.locks = locks;
        this.shardId = shardId;
    }

    // ---- Start ----

    @Override
    public CompletableFuture<BattleStartResult> start(BattleRequest request) {
        Player player = request.player();
        UUID playerId = player.getUniqueId();
        SpeciesRegistry registry = PokeCoreAPI.get().species();
        TrainerStorage storage = PokeCoreAPI.get().trainers().cached(playerId).orElse(null);
        if (storage == null)
            return CompletableFuture.completedFuture(BattleStartResult.NO_USABLE_CREATURE);

        // Fainted creatures stay out: the simulator only gets creatures that can fight
        List<Battle.Ally> allies = new ArrayList<>();
        for (Pokemon pokemon : storage.partyMembers())
            if (allies.size() < MAX_TEAM && PokemonStats.currentHp(pokemon, registry) > 0)
                allies.add(new Battle.Ally(Battle.label(allies.size()), pokemon));
        if (allies.isEmpty())
            return CompletableFuture.completedFuture(BattleStartResult.NO_USABLE_CREATURE);

        UUID battleId = UuidV7.generate();
        Battle battle = new Battle(battleId, request.format(), playerId, allies,
                List.of(new Battle.Foe(Battle.label(0), request.wild())));
        BattleStartPacket packet;
        try {
            packet = this.startPacket(battle, player.getName(), registry);
        } catch (IllegalArgumentException e) {
            this.plugin.getSLF4JLogger().error("Battle {} for {} cannot be built", battleId, player.getName(), e);
            return CompletableFuture.completedFuture(BattleStartResult.ERROR);
        }

        if (this.byPlayer.putIfAbsent(playerId, battleId) != null)
            return CompletableFuture.completedFuture(BattleStartResult.ALREADY_IN_BATTLE);
        this.battles.put(battleId, battle);

        PokeBattleConfiguration configuration = this.plugin.configuration();
        return this.locks.acquire(playerId, battleId, configuration.battle().lockTtl())
                .thenCompose(acquired -> {
                    if (!acquired) {
                        this.forget(battle);
                        return CompletableFuture.completedFuture(BattleStartResult.ALREADY_IN_BATTLE);
                    }
                    return this.transport.start(packet, configuration.start().timeout(), configuration.start().attempts())
                            .thenApply(reply -> this.onStartReply(battle, player, reply));
                })
                .exceptionally(throwable -> {
                    this.plugin.getSLF4JLogger().error("Starting battle {} for {} failed", battleId, player.getName(),
                            throwable);
                    this.discard(battle);
                    return BattleStartResult.ERROR;
                });
    }

    private BattleStartPacket startPacket(Battle battle, String playerName, SpeciesRegistry registry) {
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
        List<PackedSet> wild = battle.foes()
                .stream()
                .map(foe -> BattleSets.of(foe.creature(), foe.label(), registry))
                .toList();

        List<BattleSide> sides = List.of(
                new BattleSide(Battle.PLAYER_SIDE, playerName, battle.playerId(), PackedTeam.pack(sets), "", states),
                new BattleSide(Battle.FOE_SIDE, WILD_SIDE_NAME, null, PackedTeam.pack(wild), WILD_AI, List.of()));
        return new BattleStartPacket(battle.id(), this.shardId, battle.format(), "", sides,
                this.plugin.configuration().battle().turnTimerSeconds(), false, false);
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
            // A request that came before the reply could not be answered yet
            this.openDialog(battle);
        }
        this.plugin.debug("Battle {} for {} runs on worker {}", battle.id(), player.getName(), reply.workerId());
        if (abortReason != null)
            this.abort(battle.id(), abortReason);
        PokeBattleMessages.REOPEN_HINT.message(player);
        Bukkit.getPluginManager().callEvent(new BattleStartEvent(battle.id(), player, battle.format()));
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
            this.plugin.debug("Packet {} for unknown battle {}", packet.getClass().getSimpleName(), battleId);
            return;
        }
        synchronized (battle) {
            for (Packet ready : battle.sequence().offer(seq, packet))
                this.process(battle, ready);
        }
    }

    private void process(Battle battle, Packet packet) {
        if (battle.state() == Battle.State.ENDED)
            return;
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

    private void onUpdate(Battle battle, BattleUpdatePacket update) {
        if (!update.side().equals(Battle.PLAYER_SIDE))
            return;
        Player player = Bukkit.getPlayer(battle.playerId());
        BattleLogRenderer renderer = new BattleLogRenderer(battle, player == null ? null : player.locale());
        List<Component> log = new ArrayList<>();
        for (String raw : update.lines()) {
            ProtocolLine line = ProtocolLine.parse(raw);
            if (line == null)
                continue;
            battle.tracker().accept(line);
            if (player != null) {
                Component rendered = renderer.render(line);
                if (rendered != null)
                    log.add(rendered);
            }
        }
        if (player != null)
            log.forEach(player::sendMessage);
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
        this.locks.refresh(battle.playerId(), battle.id(), this.plugin.configuration().battle().lockTtl())
                .exceptionally(throwable -> {
                    this.plugin.getSLF4JLogger().warn("Battle {}: lock refresh failed", battle.id(), throwable);
                    return false;
                });
        this.openDialog(battle);
    }

    private void onChoiceError(Battle battle, BattleChoiceErrorPacket error) {
        ChoiceRequest pending = battle.pending();
        if (pending == null || pending.rqid() != error.rqid())
            return;
        this.plugin.debug("Battle {}: choice refused ({})", battle.id(), error.message());
        battle.chosen(false);
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
        ChoiceDialogs dialogs = this.plugin.dialogs();
        player.getScheduler().run(this.plugin, _ -> dialogs.open(player, view), null);
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
        this.submit(player, battle, view.rqid(), choice);
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
        synchronized (battle) {
            ChoiceRequest pending = battle.pending();
            rqid = pending == null ? -1 : pending.rqid();
        }
        this.submit(player, battle, rqid, choice);
    }

    private void submit(Player player, Battle battle, int rqid, String choice) {
        String workerId;
        synchronized (battle) {
            ChoiceRequest request = battle.pending();
            if (battle.state() != Battle.State.RUNNING || request == null || !request.choosable() || battle.chosen()
                    || request.rqid() != rqid) {
                PokeBattleMessages.WAITING_FOR_TURN.message(player);
                return;
            }
            if (!legal(request, choice)) {
                PokeBattleMessages.CHOICE_UNAVAILABLE.message(player);
                this.openDialog(battle);
                return;
            }
            battle.chosen(true);
            workerId = battle.workerId();
        }

        this.transport.choose(workerId, battle.id(), Battle.PLAYER_SIDE, rqid, choice).exceptionally(throwable -> {
            this.plugin.getSLF4JLogger().error("Battle {}: sending a choice failed", battle.id(), throwable);
            PokeBattleMessages.UNEXPECTED_ERROR.message(player);
            synchronized (battle) {
                ChoiceRequest pending = battle.pending();
                if (pending != null && pending.rqid() == rqid) {
                    battle.chosen(false);
                    this.openDialog(battle);
                }
            }
            return null;
        });
    }

    /**
     * The choices the dialog and the commands send, checked against the request before they leave this server.
     */
    static boolean legal(ChoiceRequest request, String choice) {
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
            if (this.battles.get(battleId) == battle && battle.state() != Battle.State.ENDED) {
                this.plugin.getSLF4JLogger().warn("Battle {}: worker {} did not confirm the stop, releasing the player",
                        battleId, workerId);
                synchronized (battle) {
                    battle.state(Battle.State.ENDED);
                }
                this.finish(battle, BattleOutcome.ABORTED, 0, false);
            }
        }, this.plugin.configuration().battle().endTimeout().toMillis(), TimeUnit.MILLISECONDS);
        return true;
    }

    private void settle(Battle battle, BattleEndedPacket ended) {
        BattleOutcome outcome = switch (ended.result()) {
            case Battle.PLAYER_SIDE -> BattleOutcome.WON;
            case "tie" -> BattleOutcome.TIE;
            case "aborted" -> BattleOutcome.ABORTED;
            default -> BattleOutcome.LOST;
        };
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
                        state.status().isEmpty() ? null : state.status(), state.pp()));
            allies.put(ally.label(), new RewardCalculator.Ally(ally.pokemon().id(), ally.pokemon().version(),
                    ally.pokemon().level(), state != null && state.hp() > 0));
        }

        List<ExperienceGain> gains = List.of();
        if (outcome == BattleOutcome.WON) {
            SpeciesRegistry registry = PokeCoreAPI.get().species();
            Map<String, RewardCalculator.Foe> foes = new LinkedHashMap<>();
            for (Battle.Foe foe : battle.foes()) {
                Species species = registry.findSpecies(foe.creature().species()).orElse(null);
                if (species != null)
                    foes.put(foe.label(), new RewardCalculator.Foe(species.baseExperience(), foe.creature().level(),
                            species.evYield()));
            }
            gains = RewardCalculator.gains(battle.tracker().defeats(), allies, foes,
                    this.plugin.configuration().rewards().rules());
        }

        List<ExperienceGain> granted = gains;
        this.settle(battle.playerId(), conditions, gains, true)
                .thenAccept(result -> {
                    this.report(battle, outcome, granted, result.orElse(null));
                    this.finish(battle, outcome, ended.turns(), result.isPresent());
                })
                .exceptionally(throwable -> {
                    this.plugin.getSLF4JLogger().error("Battle {}: settlement failed", battle.id(), throwable);
                    this.report(battle, outcome, List.of(), null);
                    this.finish(battle, outcome, ended.turns(), false);
                    return null;
                });
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
                                            condition.hp(), condition.status(), condition.pp()))
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
     * @param progressions null when nothing was written
     */
    private void report(Battle battle, BattleOutcome outcome, List<ExperienceGain> gains,
                        @Nullable List<Progression> progressions) {
        Player player = Bukkit.getPlayer(battle.playerId());
        if (player == null)
            return;
        switch (outcome) {
            case WON -> PokeBattleMessages.BATTLE_WON.message(player);
            case LOST -> PokeBattleMessages.BATTLE_LOST.message(player);
            case TIE -> PokeBattleMessages.BATTLE_TIE.message(player);
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

    private void finish(Battle battle, BattleOutcome outcome, int turns, boolean settled) {
        this.forget(battle);
        this.locks.release(battle.playerId(), battle.id()).exceptionally(throwable -> {
            this.plugin.getSLF4JLogger().warn("Battle {}: lock release failed", battle.id(), throwable);
            return false;
        });
        Bukkit.getPluginManager().callEvent(new BattleEndEvent(battle.id(), battle.playerId(), battle.format(), outcome,
                turns, settled));
    }

    /**
     * Drops a battle that never ran (no worker, refused, failed): nothing to settle.
     */
    private void discard(Battle battle) {
        this.forget(battle);
        this.locks.release(battle.playerId(), battle.id()).exceptionally(_ -> false);
    }

    private void forget(Battle battle) {
        this.battles.remove(battle.id(), battle);
        this.byPlayer.remove(battle.playerId(), battle.id());
    }

    // ---- Connection ----

    /**
     * A player in battle left: the battle waits for them, then is lost with no penalty.
     */
    public void onQuit(UUID playerId) {
        UUID battleId = this.byPlayer.get(playerId);
        Battle battle = battleId == null ? null : this.battles.get(battleId);
        if (battle == null)
            return;
        Duration grace = this.plugin.configuration().battle().disconnectGrace();
        synchronized (battle) {
            this.cancelDisconnect(battle);
            battle.disconnectTask(Bukkit.getAsyncScheduler().runDelayed(this.plugin,
                    _ -> this.abort(battleId, "disconnected"), grace.toMillis(), TimeUnit.MILLISECONDS));
        }
    }

    public void onJoin(Player player) {
        Battle battle = this.battleFor(player);
        if (battle == null)
            return;
        synchronized (battle) {
            this.cancelDisconnect(battle);
            this.openDialog(battle);
        }
    }

    private void cancelDisconnect(Battle battle) {
        if (battle.disconnectTask() != null) {
            battle.disconnectTask().cancel();
            battle.disconnectTask(null);
        }
    }

    /**
     * Server stopping: stops every battle (no settlement, creatures keep their state from before it) and releases
     * the players. Recovery on another server comes later.
     */
    public void shutdown() {
        List<CompletableFuture<?>> pending = new ArrayList<>();
        for (Battle battle : List.copyOf(this.battles.values())) {
            String workerId = battle.workerId();
            if (workerId != null)
                pending.add(this.transport.end(workerId, battle.id(), "aborted", "server stopping")
                        .exceptionally(_ -> null));
            pending.add(this.locks.release(battle.playerId(), battle.id()).exceptionally(_ -> false));
            this.forget(battle);
        }
        try {
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            this.plugin.getSLF4JLogger().warn("Some battles could not be stopped cleanly", e);
        }
    }
}
