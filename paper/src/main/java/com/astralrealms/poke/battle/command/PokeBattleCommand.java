package com.astralrealms.poke.battle.command;

import java.util.Set;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.astralrealms.core.placeholder.container.PlaceholderContainer;
import com.astralrealms.poke.battle.PokeBattle;
import com.astralrealms.poke.battle.arena.ArenaBattle;
import com.astralrealms.poke.battle.arena.ArenaRequests;
import com.astralrealms.poke.battle.api.model.BattleRequest;
import com.astralrealms.poke.battle.api.model.BattleStartResult;
import com.astralrealms.poke.battle.configuration.PokeBattleMessages;
import com.astralrealms.poke.core.PokeCoreAPI;
import com.astralrealms.poke.core.mechanics.generation.GeneratedCreature;
import com.astralrealms.poke.core.mechanics.generation.GenerationOptions;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;

@CommandAlias("pokebattle|pkb")
@CommandPermission("pokebattle.admin")
@Description("PokeBattle administration")
public class PokeBattleCommand extends BaseCommand {

    @Dependency
    private PokeBattle plugin;

    @Subcommand("reload")
    @Description("Reload the configuration and messages")
    public void onReload(CommandSender sender) {
        this.plugin.loadConfiguration();
        PokeBattleMessages.RELOADED.message(sender);
    }

    @Subcommand("wild")
    @Description("Start a wild battle against a freshly rolled creature")
    @Syntax("<player> <species> [level]")
    @CommandCompletion("@players @species @nothing")
    public void onWild(CommandSender sender, OnlinePlayer target, String species, @Default("5") int level) {
        Player player = target.getPlayer();
        GeneratedCreature creature;
        try {
            creature = PokeCoreAPI.get().creation().generate(species, null, level, GenerationOptions.DEFAULT);
        } catch (IllegalArgumentException e) {
            PokeBattleMessages.UNKNOWN_SPECIES.message(sender, new PlaceholderContainer()
                    .registerDirect("species", species));
            return;
        }
        // Signed as a spawn would sign it: catching it redeems this roll
        this.plugin.battles()
                .start(BattleRequest.wild(player, PokeCoreAPI.get().creation().sign(creature)))
                .thenAccept(result -> report(player, result));
    }

    @Subcommand("arena")
    @Description("Send a player to battle a freshly rolled creature in an arena (an instance on an arena server)")
    @Syntax("<player> <template> <species> [level]")
    @CommandCompletion("@players @nothing @species @nothing")
    public void onArena(CommandSender sender, OnlinePlayer target, String template, String species,
                        @Default("5") int level) {
        ArenaRequests arenas = this.plugin.arenas();
        if (arenas == null) {
            PokeBattleMessages.ARENA_UNAVAILABLE.message(sender);
            return;
        }
        if (PokeCoreAPI.get().species().findSpecies(species).isEmpty()) {
            PokeBattleMessages.UNKNOWN_SPECIES.message(sender, new PlaceholderContainer()
                    .registerDirect("species", species));
            return;
        }
        arenas.request(target.getPlayer(), template,
                new ArenaBattle(BattleRequest.WILD_FORMAT, species, null, level, Set.of()));
    }

    @Subcommand("abort")
    @Description("Stop a player's battle with no winner and no rewards")
    @Syntax("<player>")
    @CommandCompletion("@players")
    public void onAbort(CommandSender sender, OnlinePlayer target) {
        Player player = target.getPlayer();
        PlaceholderContainer placeholders = new PlaceholderContainer().registerDirect("player", player.getName());
        UUID battleId = this.plugin.battles().battleOf(player.getUniqueId()).orElse(null);
        if (battleId == null || !this.plugin.battles().abort(battleId, "aborted by " + sender.getName())) {
            PokeBattleMessages.PLAYER_NOT_IN_BATTLE.message(sender, placeholders);
            return;
        }
        PokeBattleMessages.BATTLE_ABORTED_BY_ADMIN.message(sender, placeholders);
    }

    /**
     * Tells the player why their battle did not start; nothing when it did (the battle log follows).
     */
    public static void report(Player player, BattleStartResult result) {
        PokeBattleMessages message = switch (result) {
            case STARTED -> null;
            case ALREADY_IN_BATTLE -> PokeBattleMessages.ALREADY_IN_BATTLE;
            case NO_USABLE_CREATURE -> PokeBattleMessages.NO_USABLE_CREATURE;
            case NO_WORKER -> PokeBattleMessages.NO_BATTLE_WORKER;
            case REFUSED -> PokeBattleMessages.BATTLE_REFUSED;
            case ERROR -> PokeBattleMessages.UNEXPECTED_ERROR;
        };
        if (message != null)
            message.message(player);
    }
}
