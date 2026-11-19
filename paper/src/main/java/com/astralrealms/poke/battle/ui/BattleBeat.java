package com.astralrealms.poke.battle.ui;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.battle.model.Battle;
import com.astralrealms.poke.battle.protocol.PokemonRef;
import com.astralrealms.poke.battle.protocol.ProtocolLine;

import net.kyori.adventure.text.Component;

/**
 * One moment of an update, played in turn so the field changes in step with what is shown: what happened
 * ({@code cue}), the field right after it, and its log lines (with those that led to it).
 *
 * @param cue    null for the lines after an update's last cue (the turn counter, end-of-turn effects)
 * @param move   for a move cue, the move's id when it is shown ({@link BattleCue#move}); null otherwise
 * @param slot   the slot of the creature the cue is about ({@code 0} for {@code p1a}/{@code p2a}, {@code 1} for
 *               {@code b} in doubles)
 * @param target for a move cue, the creature it was aimed at; null when the line names none (then the creature
 *               across)
 */
public record BattleBeat(@Nullable BattleCue cue, @Nullable String move, FieldView field, List<Component> log,
                         int slot, @Nullable Spot target) {

    public BattleBeat {
        log = List.copyOf(log);
        slot = Math.max(0, slot);
    }

    /**
     * A beat about the first slot, aimed at nobody in particular (singles).
     */
    public BattleBeat(@Nullable BattleCue cue, @Nullable String move, FieldView field, List<Component> log) {
        this(cue, move, field, log, 0, null);
    }

    /**
     * The beat of a protocol line: the slot of the creature it names, and the target of a move.
     */
    public static BattleBeat of(BattleCue cue, ProtocolLine line, FieldView field, List<Component> log) {
        PokemonRef actor = PokemonRef.parse(line.arg(0));
        PokemonRef target = line.type().equals("move") ? PokemonRef.parse(line.arg(2)) : null;
        return new BattleBeat(cue, BattleCue.move(line), field, log, actor == null ? 0 : actor.slot(),
                target == null || target.slot() < 0 ? null : new Spot(target.side().equals(Battle.PLAYER_SIDE),
                        target.slot()));
    }

    /**
     * A creature's place on the field.
     *
     * @param ally whether it is the player's
     * @param slot 0 for {@code a}, 1 for {@code b}
     */
    public record Spot(boolean ally, int slot) {
    }
}
