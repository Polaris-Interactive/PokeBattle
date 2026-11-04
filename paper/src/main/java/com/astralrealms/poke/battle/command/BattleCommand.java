package com.astralrealms.poke.battle.command;

import org.bukkit.entity.Player;

import com.astralrealms.poke.battle.PokeBattle;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;

/**
 * {@code /combat}: reopens the choice dialog. The sub-commands choose without it (Bedrock players, until their
 * forms exist).
 */
@CommandAlias("combat|battle")
@Description("Combat en cours")
public class BattleCommand extends BaseCommand {

    @Dependency
    private PokeBattle plugin;

    @Default
    @Description("Rouvrir le choix d'action")
    public void onOpen(Player player) {
        this.plugin.battles().reopen(player);
    }

    @Subcommand("attaque")
    @Description("Utiliser une capacité")
    @Syntax("<1-4>")
    @CommandCompletion("1|2|3|4")
    public void onMove(Player player, int index) {
        this.plugin.battles().choose(player, "move " + index);
    }

    @Subcommand("changer")
    @Description("Envoyer une autre créature")
    @Syntax("<position>")
    @CommandCompletion("1|2|3|4|5|6")
    public void onSwitch(Player player, int position) {
        this.plugin.battles().choose(player, "switch " + position);
    }
}
