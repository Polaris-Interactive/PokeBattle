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

    /**
     * In doubles, the pick of the creature being chosen for; a move that needs a target takes it as Showdown numbers
     * it (the opponent's positions 1 and 2, the player's own -1 and -2).
     */
    @Subcommand("attaque")
    @Description("Utiliser une capacité")
    @Syntax("<1-4> [cible]")
    @CommandCompletion("1|2|3|4 1|2|-1|-2")
    public void onMove(Player player, int index, @Optional Integer target) {
        this.plugin.battles().choose(player, "move " + index + (target == null ? "" : " " + target));
    }

    @Subcommand("fuite")
    @Description("Prendre la fuite")
    public void onRun(Player player) {
        this.plugin.battles().choose(player, "run");
    }

    @Subcommand("changer")
    @Description("Envoyer une autre créature")
    @Syntax("<position>")
    @CommandCompletion("1|2|3|4|5|6")
    public void onSwitch(Player player, int position) {
        this.plugin.battles().choose(player, "switch " + position);
    }
}
