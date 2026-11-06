package com.astralrealms.poke.battle.ui;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.text.Component;

/**
 * One moment of an update, played in turn so the field changes in step with what is shown: what happened
 * ({@code cue}), the field right after it, and its log lines (with those that led to it).
 *
 * @param cue null for the lines after an update's last cue (the turn counter, end-of-turn effects)
 */
public record BattleBeat(@Nullable BattleCue cue, FieldView field, List<Component> log) {

    public BattleBeat {
        log = List.copyOf(log);
    }
}
