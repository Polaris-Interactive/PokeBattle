package com.astralrealms.poke.battle.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.battle.model.Battle;
import com.astralrealms.poke.battle.model.Names;

/**
 * The field as the player sees it: their creatures (exact HP) and the opponent's (HP as a percentage), by slot: one
 * each in singles, two in doubles. Taken from a battle under its monitor, read on the player's thread.
 *
 * @param turn   0 before the first turn
 * @param allies the player's creatures by slot ({@code p1a} first), never empty
 * @param foes   the opponent's by slot, never empty
 */
public record FieldView(UUID battleId, int turn, List<ChoiceView.Creature> allies, List<ChoiceView.Creature> foes) {

    private static final ChoiceView.Creature NOBODY = new ChoiceView.Creature("", 0, 0, 0, "", "", "");

    public FieldView {
        allies = allies.isEmpty() ? List.of(NOBODY) : List.copyOf(allies);
        foes = foes.isEmpty() ? List.of(NOBODY) : List.copyOf(foes);
    }

    /**
     * A singles field.
     */
    public FieldView(UUID battleId, int turn, ChoiceView.Creature ally, ChoiceView.Creature foe) {
        this(battleId, turn, List.of(ally), List.of(foe));
    }

    /**
     * Must run under the battle's monitor.
     */
    public static FieldView of(Battle battle, @Nullable Locale locale) {
        int slots = Math.max(1, battle.tracker().slots());
        List<ChoiceView.Creature> allies = new ArrayList<>();
        List<ChoiceView.Creature> foes = new ArrayList<>();
        for (int slot = 0; slot < slots; slot++) {
            ChoiceView.Creature ally = NOBODY;
            String allyLabel = battle.tracker().active(Battle.PLAYER_SIDE, slot);
            Battle.Ally member = allyLabel == null ? null : battle.ally(allyLabel).orElse(null);
            if (member != null)
                ally = ChoiceView.creature(Names.ally(locale, member), member.pokemon().species(),
                        member.pokemon().level(), battle.tracker().condition(Battle.PLAYER_SIDE, allyLabel), true,
                        locale);
            allies.add(ally);

            ChoiceView.Creature foe = NOBODY;
            String foeLabel = battle.tracker().active(Battle.FOE_SIDE, slot);
            Battle.Foe opponent = foeLabel == null ? null : battle.foe(foeLabel).orElse(null);
            if (opponent != null)
                foe = ChoiceView.creature(Names.foe(locale, opponent), opponent.species(),
                        opponent.level(), battle.tracker().condition(Battle.FOE_SIDE, foeLabel), false, locale);
            foes.add(foe);
        }
        return new FieldView(battle.id(), battle.tracker().turn(), allies, foes);
    }

    public static FieldView of(ChoiceView view) {
        ChoiceView.Doubles doubles = view.doubles();
        if (doubles != null)
            return new FieldView(view.battleId(), view.turn(), doubles.allies(), doubles.foes());
        return new FieldView(view.battleId(), view.turn(), view.ally(), view.foe());
    }

    /**
     * The player's creature on the field (the first slot's in doubles).
     */
    public ChoiceView.Creature ally() {
        return this.allies.getFirst();
    }

    /**
     * The opponent's creature on the field (the first slot's in doubles).
     */
    public ChoiceView.Creature foe() {
        return this.foes.getFirst();
    }

    /**
     * The player's creature at a slot, nobody past the last one.
     */
    public ChoiceView.Creature ally(int slot) {
        return slot >= 0 && slot < this.allies.size() ? this.allies.get(slot) : NOBODY;
    }

    /**
     * The opponent's creature at a slot, nobody past the last one.
     */
    public ChoiceView.Creature foe(int slot) {
        return slot >= 0 && slot < this.foes.size() ? this.foes.get(slot) : NOBODY;
    }

    /**
     * Whether each side has more than one creature on the field.
     */
    public boolean doubles() {
        return Math.max(this.allies.size(), this.foes.size()) > 1;
    }

    /**
     * Whether a creature is on the field yet.
     */
    public static boolean present(ChoiceView.Creature creature) {
        return !creature.name().isEmpty();
    }
}
