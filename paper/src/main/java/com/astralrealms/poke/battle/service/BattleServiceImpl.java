package com.astralrealms.poke.battle.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

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
import com.astralrealms.poke.battle.render.BattleLogRenderer;
import com.astralrealms.poke.battle.ui.BattleBeat;
import com.astralrealms.poke.battle.ui.BattleCue;
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
import com.astralrealms.poke.core.mechanics.capture.CaptureContext;
import com.astralrealms.poke.core.mechanics.capture.CaptureResult;
import com.astralrealms.poke.core.mechanics.generation.GeneratedCreature;
import com.astralrealms.poke.core.mechanics.generation.SignedRoll;
import com.astralrealms.poke.core.mechanics.progression.BattleCondition;
import com.astralrealms.poke.core.mechanics.progression.ExperienceGain;
import com.astralrealms.poke.core.mechanics.progression.Progression;
import com.astralrealms.poke.core.model.MoveSlot;
import com.astralrealms.poke.core.model.Pokemon;
import com.astralrealms.poke.core.model.stat.StatSpread;
import com.astralrealms.poke.core.model.storage.StoragePosition;
import com.astralrealms.poke.core.model.storage.StorageResult;
import com.astralrealms.poke.core.model.storage.TrainerStorage;
import com.astralrealms.poke.core.species.Species;
import com.astralrealms.poke.core.species.SpeciesVariant;
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
    private static final String RUN = "run";

    private final PokeBattle plugin;
    private final BattleTransport transport;
    private final BattleLocks locks;
    private final String shardId;
    private final Map<UUID, Battle> battles = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> byPlayer = new ConcurrentHashMap<>();
    private final BattleBag bag = new BattleBag();

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

        // Standing creatures first (the lead must fight); fainted ones follow, only there to be revived from the bag
        List<Pokemon> standing = new ArrayList<>();
        List<Pokemon> fainted = new ArrayList<>();
        for (Pokemon pokemon : storage.partyMembers())
            (PokemonStats.currentHp(pokemon, registry) > 0 ? standing : fainted).add(pokemon);
        if (standing.isEmpty())
            return CompletableFuture.completedFuture(BattleStartResult.NO_USABLE_CREATURE);
        List<Battle.Ally> allies = new ArrayList<>();
        for (Pokemon pokemon : standing)
            if (allies.size() < MAX_TEAM)
                allies.add(new Battle.Ally(Battle.label(allies.size()), pokemon));
        for (Pokemon pokemon : fainted)
            if (allies.size() < MAX_TEAM)
                allies.add(new Battle.Ally(Battle.label(allies.size()), pokemon));

        UUID battleId = UuidV7.generate();
        Battle.Options options = new Battle.Options(true, true, true, request.wild(), request.captureFlags(),
                player.getWorld().getName());
        Battle battle = new Battle(battleId, request.format(), playerId, allies,
                List.of(new Battle.Foe(Battle.label(0), request.wild().creature())), options);
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
                beats.add(new BattleBeat(cue, BattleCue.move(line), FieldView.of(battle, player.locale()), log));
                log.clear();
            }
        }
        if (player == null)
            return;
        if (!log.isEmpty() || beats.isEmpty())
            beats.add(new BattleBeat(null, null, FieldView.of(battle, player.locale()), log));
        this.withUi(battle.playerId(), (ui, viewer) -> beats.forEach(beat -> ui.update(viewer, beat)));
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
        // A new request: the item spent on the last choice was used
        battle.spent(null);
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
        this.withUi(battle.playerId(), (ui, viewer) -> ui.choose(viewer, view));
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
        if (player == null)
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
            BattleBag.Taken taken = this.bag.take(player, ball);
            if (taken == null) {
                PokeBattleMessages.ITEM_MISSING.message(player);
                this.openDialog(battle);
                return;
            }
            CaptureResult result = PokeCoreAPI.get().capture().roll(attempt);
            this.plugin.debug("Battle {}: {} thrown, {} shakes, caught {}", battle.id(), ball, result.shakes(),
                    result.caught());
            battle.itemNames().put(ball, taken.name());
            battle.spent(new Battle.Spent(view.rqid(), ball));
            battle.chosen(true);
            workerId = battle.workerId();
            choice = "ball " + ball + " " + result.shakes() + " " + (result.caught() ? 1 : 0);
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
            battle.itemNames().put(item, taken.name());
            battle.spent(new Battle.Spent(view.rqid(), item));
            battle.chosen(true);
            workerId = battle.workerId();
            choice = "item " + item + " " + position + " " + BattleBag.encode(taken.effects());
        }
        this.send(player, battle, workerId, view.rqid(), choice);
    }

    @Override
    public BattleBag bag() {
        return this.bag;
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
    private @Nullable CaptureAttempt attempt(Battle battle, String ball) {
        if (PokeCoreAPI.get().capture().ball(ball).isEmpty() || battle.options().wildRoll() == null)
            return null;
        String foeLabel = battle.tracker().active(Battle.FOE_SIDE);
        Battle.Foe foe = foeLabel == null ? null : battle.foe(foeLabel).orElse(null);
        Condition condition = foeLabel == null ? null : battle.tracker().condition(Battle.FOE_SIDE, foeLabel);
        if (foe == null || condition == null || condition.fainted())
            return null;

        SpeciesRegistry registry = PokeCoreAPI.get().species();
        GeneratedCreature creature = foe.creature();
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
        Battle.Spent spent = battle.spent();
        if (spent == null || spent.rqid() != rqid)
            return;
        battle.spent(null);
        Player player = Bukkit.getPlayer(battle.playerId());
        if (player == null) {
            this.plugin.getSLF4JLogger().warn("Battle {}: {} could not be given back to {} (offline)", battle.id(),
                    spent.itemId(), battle.playerId());
            return;
        }
        Component name = battle.itemNames().get(spent.itemId());
        player.getScheduler().run(this.plugin, _ -> {
            if (this.bag.refund(player, spent.itemId()))
                PokeBattleMessages.ITEM_REFUNDED.message(player, new PlaceholderContainer()
                        .registerDirect("item", name == null ? spent.itemId() : name));
            else
                this.plugin.getSLF4JLogger().warn("Battle {}: {} has no item stack any more, not given back",
                        battle.id(), spent.itemId());
        }, null);
    }

    /**
     * The choices the dialog and the commands send, checked against the request before they leave this server. Balls
     * and bag items are built here, not typed: they have their own checks.
     */
    static boolean legal(ChoiceRequest request, String choice) {
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
        BattleTracker.Capture capture = battle.tracker().capture();
        BattleOutcome outcome = switch (ended.result()) {
            case Battle.PLAYER_SIDE -> capture != null ? BattleOutcome.CAPTURED : BattleOutcome.WON;
            case "tie" -> BattleOutcome.TIE;
            case "fled" -> BattleOutcome.FLED;
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

        // Rewards for a win; for a catch too (modern rule, configurable): the caught opponent counts as defeated
        List<ExperienceGain> gains = List.of();
        boolean rewarded = outcome == BattleOutcome.WON
                || (outcome == BattleOutcome.CAPTURED && this.plugin.configuration().rewards().experienceOnCapture());
        if (rewarded) {
            SpeciesRegistry registry = PokeCoreAPI.get().species();
            Map<String, RewardCalculator.Foe> foes = new LinkedHashMap<>();
            for (Battle.Foe foe : battle.foes()) {
                Species species = registry.findSpecies(foe.creature().species()).orElse(null);
                if (species != null)
                    foes.put(foe.label(), new RewardCalculator.Foe(species.baseExperience(), foe.creature().level(),
                            species.evYield()));
            }
            List<BattleTracker.Defeat> defeats = new ArrayList<>(battle.tracker().defeats());
            if (capture != null)
                defeats.add(new BattleTracker.Defeat(capture.label(), capture.participants()));
            gains = RewardCalculator.gains(defeats, allies, foes, this.plugin.configuration().rewards().rules());
        }

        List<ExperienceGain> granted = gains;
        CompletableFuture<@Nullable CreationResult> caught = outcome == BattleOutcome.CAPTURED
                ? this.redeem(battle, capture)
                : CompletableFuture.completedFuture(null);
        caught.thenCompose(creation -> {
                    // The caught creature keeps the condition the battle left it in, like the team
                    List<BattleCondition> all = new ArrayList<>(conditions);
                    BattleCondition captured = this.capturedCondition(battle, capture, creation, ended);
                    if (captured != null)
                        all.add(captured);
                    return this.settle(battle.playerId(), all, granted, true).thenAccept(result -> {
                        this.report(battle, outcome, creation, granted, result.orElse(null));
                        this.finish(battle, outcome, ended.turns(), result.isPresent());
                    });
                })
                .exceptionally(throwable -> {
                    this.plugin.getSLF4JLogger().error("Battle {}: settlement failed", battle.id(), throwable);
                    this.report(battle, outcome, null, List.of(), null);
                    this.finish(battle, outcome, ended.turns(), false);
                    return null;
                });
    }

    /**
     * Stores the caught creature: the signed roll it appeared with, redeemed once. A failure (storage full, invalid
     * or already redeemed roll) still settles the rest of the battle.
     */
    private CompletableFuture<@Nullable CreationResult> redeem(Battle battle,
                                                               BattleTracker.@Nullable Capture capture) {
        SignedRoll roll = battle.options().wildRoll();
        if (capture == null || roll == null)
            return CompletableFuture.completedFuture(null);
        return PokeCoreAPI.get()
                .creation()
                .redeem(battle.playerId(), roll, CreationOptions.capture(capture.ball(), battle.options().metAt(),
                        battle.id().toString()))
                .<@Nullable CreationResult>thenApply(result -> {
                    if (!result.succeeded())
                        this.plugin.getSLF4JLogger().warn("Battle {}: the caught creature was not stored ({})",
                                battle.id(), result.status());
                    return result;
                })
                .exceptionally(throwable -> {
                    this.plugin.getSLF4JLogger().error("Battle {}: storing the caught creature failed", battle.id(),
                            throwable);
                    return null;
                });
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
                state.status().isEmpty() ? null : state.status(), state.pp());
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
    private void report(Battle battle, BattleOutcome outcome, @Nullable CreationResult creation,
                        List<ExperienceGain> gains, @Nullable List<Progression> progressions) {
        Player player = Bukkit.getPlayer(battle.playerId());
        if (player == null)
            return;
        switch (outcome) {
            case WON -> PokeBattleMessages.BATTLE_WON.message(player);
            case CAPTURED -> this.reportCapture(player, battle, creation);
            case LOST -> PokeBattleMessages.BATTLE_LOST.message(player);
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
        this.withUi(battle.playerId(), (ui, viewer) -> ui.close(viewer, battle.id()));
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
        this.withUi(battle.playerId(), (ui, viewer) -> ui.close(viewer, battle.id()));
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
            this.showField(battle);
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
