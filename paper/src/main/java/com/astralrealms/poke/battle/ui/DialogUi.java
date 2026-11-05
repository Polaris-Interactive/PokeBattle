package com.astralrealms.poke.battle.ui;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

/**
 * The battle in the chat, choices in a dialog. Works for every player, without AstralHologram.
 */
public class DialogUi implements BattleUi {

    private final Supplier<ChoiceDialogs> dialogs;

    /**
     * @param dialogs read on every choice, so a reload applies at once
     */
    public DialogUi(Supplier<ChoiceDialogs> dialogs) {
        this.dialogs = dialogs;
    }

    @Override
    public void open(Player player, FieldView field) {
    }

    @Override
    public void update(Player player, FieldView field, List<Component> log, List<BattleCue> cues) {
        log.forEach(player::sendMessage);
    }

    @Override
    public void choose(Player player, ChoiceView view) {
        this.dialogs.get().open(player, view);
    }

    @Override
    public void waiting(Player player) {
    }

    @Override
    public void close(Player player, UUID battleId) {
    }

    @Override
    public void shutdown() {
    }
}
