package com.astralrealms.poke.battle;

import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;

import com.astralrealms.core.cache.CacheService;
import com.astralrealms.core.messaging.MessagingService;
import com.astralrealms.core.paper.AstralPaperAPI;
import com.astralrealms.core.paper.plugin.AstralPaperPlugin;
import com.astralrealms.poke.battle.arena.ArenaBattles;
import com.astralrealms.poke.battle.arena.ArenaRequests;
import com.astralrealms.poke.battle.command.BattleCommand;
import com.astralrealms.poke.battle.command.PokeBattleCommand;
import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;
import com.astralrealms.poke.battle.configuration.PokeBattleMessages;
import com.astralrealms.poke.battle.listener.BattleConnectionListener;
import com.astralrealms.poke.battle.listener.StagingListener;
import com.astralrealms.poke.battle.network.BattleTransport;
import com.astralrealms.poke.battle.network.PokeBattleExchanges;
import com.astralrealms.poke.battle.network.PokeBattlePacketRegistry;
import com.astralrealms.poke.battle.network.ShardPacketListener;
import com.astralrealms.poke.battle.service.BattleLocks;
import com.astralrealms.poke.battle.service.BattleRecords;
import com.astralrealms.poke.battle.service.BattleServiceImpl;
import com.astralrealms.poke.battle.ui.BattleStages;
import com.astralrealms.poke.battle.ui.BattleUi;
import com.astralrealms.poke.battle.ui.ChoiceDialogs;
import com.astralrealms.poke.battle.ui.DialogUi;
import com.astralrealms.poke.battle.ui.PacedUi;
import com.astralrealms.poke.battle.ui.hud.HudUi;
import com.astralrealms.poke.battle.ui.stage.AstralSkillEffects;
import com.astralrealms.poke.battle.ui.stage.CreatureActors;
import com.astralrealms.poke.battle.ui.stage.MoveEffects;
import com.astralrealms.poke.battle.ui.stage.PokeEntitiesActors;
import com.astralrealms.poke.battle.ui.stage.StagedUi;
import com.astralrealms.poke.core.PokeCoreAPI;
import com.astralrealms.poke.core.species.Species;

import lombok.Getter;

@Getter
public final class PokeBattle extends AstralPaperPlugin {

    // Configuration
    private volatile PokeBattleConfiguration configuration;
    private volatile ChoiceDialogs dialogs;
    private BattleUi ui;
    private final BattleStages stages = new BattleStages();

    // Infrastructure
    private MessagingService messaging;
    private CacheService cache;

    // Services
    private BattleServiceImpl battles;
    /** Sends players to arenas, null without PolarisInstances-Bridge. */
    private @Nullable ArenaRequests arenas;

    @Override
    public void onEnable() {
        super.onEnable();

        // Configuration
        this.loadConfiguration();

        // Infrastructure
        this.messaging = new MessagingService(this, AstralPaperAPI.credentialsProvider(), new PokeBattlePacketRegistry());
        this.messaging.connect();
        this.cache = new CacheService(this, AstralPaperAPI.credentialsProvider());
        this.cache.connect();

        // Services
        String shardId = AstralPaperAPI.serverInformation().id();
        this.battles = new BattleServiceImpl(this, new BattleTransport(this.messaging), new BattleLocks(this.cache),
                new BattleRecords(this.cache), shardId);
        this.dialogs = new ChoiceDialogs(this.battles, this.configuration.dialog().moveColumns());
        DialogUi dialogUi = new DialogUi(this::dialogs);
        BattleUi chosen = dialogUi;
        if (this.getServer().getPluginManager().isPluginEnabled("AstralHologram")) {
            HudUi hudUi = new HudUi(this, this.battles, dialogUi);
            this.registerListeners(hudUi);
            chosen = hudUi;
        }
        // The creatures on stage, whichever UI the player has; PokeEntities' types are only loaded when it is there
        CreatureActors actors = this.getServer().getPluginManager().isPluginEnabled("PokeEntities")
                ? new PokeEntitiesActors(this.getSLF4JLogger())
                : null;
        // What moves look like on stage; AstralSkill's types are only loaded when it is there
        MoveEffects effects = this.getServer().getPluginManager().isPluginEnabled("AstralSkill")
                ? new AstralSkillEffects(this)
                : null;
        this.ui = new PacedUi(this, new StagedUi(this, chosen, actors, effects));
        this.messaging.registerExchange(PokeBattleExchanges.shard(shardId), new ShardPacketListener(this, this.battles));
        PokeBattleAPI.initialize(new PokeBattleApiImpl(this));

        // Listeners
        this.registerListeners(new BattleConnectionListener(this.battles));
        StagingListener staging = new StagingListener(this);
        this.registerListeners(staging);
        staging.start();
        // Arenas: PolarisInstances' types are only loaded when it is there
        if (this.getServer().getPluginManager().isPluginEnabled("PolarisInstances"))
            this.registerListeners(new ArenaBattles(this));
        if (this.getServer().getPluginManager().isPluginEnabled("PolarisInstances-Bridge"))
            this.arenas = new ArenaRequests(this);

        // Tasks: recover the battles of dead workers, settle ends this server missed
        long checkInterval = this.configuration.recoveryOrDefault().checkInterval().toMillis();
        this.getServer().getAsyncScheduler().runAtFixedRate(this, _ -> this.battles.checkBattles(), checkInterval,
                checkInterval, TimeUnit.MILLISECONDS);

        // Commands
        this.registerAsyncCompletion("species", _ -> PokeCoreAPI.get()
                .species()
                .enabledSpecies()
                .stream()
                .map(Species::id)
                .toList());
        this.registerCommand(new BattleCommand());
        this.registerCommand(new PokeBattleCommand());
    }

    @Override
    public void onDisable() {
        super.onDisable();

        if (this.battles != null)
            this.battles.shutdown();
        if (this.ui != null)
            this.ui.shutdown();
        if (this.cache != null)
            this.cache.disconnect();
        if (this.messaging != null)
            this.messaging.disconnect();
    }

    @Override
    public void loadConfiguration() {
        this.configuration = this.loadConfiguration("config.yml", PokeBattleConfiguration.class);
        this.loadEnum("messages.yml", PokeBattleMessages.class);
        if (this.battles != null)
            this.dialogs = new ChoiceDialogs(this.battles, this.configuration.dialog().moveColumns());
    }

    /**
     * Logs only with {@code settings.debug}.
     */
    public void debug(String message, Object... arguments) {
        if (this.configuration.settings().debug())
            this.getSLF4JLogger().info(message, arguments);
    }
}
