package com.astralrealms.poke.battle.ui;

import java.util.Locale;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.battle.model.Battle;
import com.astralrealms.poke.battle.model.Names;

/**
 * The field as the player sees it: their creature (exact HP) and the opponent (HP as a percentage). Taken from a
 * battle under its monitor, read on the player's thread.
 *
 * @param turn 0 before the first turn
 */
public record FieldView(UUID battleId, int turn, ChoiceView.Creature ally, ChoiceView.Creature foe) {

    private static final ChoiceView.Creature NOBODY = new ChoiceView.Creature("", 0, 0, 0, "", "", "");

    /**
     * Must run under the battle's monitor.
     */
    public static FieldView of(Battle battle, @Nullable Locale locale) {
        ChoiceView.Creature ally = NOBODY;
        String allyLabel = battle.tracker().active(Battle.PLAYER_SIDE);
        Battle.Ally member = allyLabel == null ? null : battle.ally(allyLabel).orElse(null);
        if (member != null)
            ally = ChoiceView.creature(Names.ally(locale, member), member.pokemon().species(),
                    member.pokemon().level(), battle.tracker().condition(Battle.PLAYER_SIDE, allyLabel), true, locale);

        ChoiceView.Creature foe = NOBODY;
        String foeLabel = battle.tracker().active(Battle.FOE_SIDE);
        Battle.Foe opponent = foeLabel == null ? null : battle.foe(foeLabel).orElse(null);
        if (opponent != null)
            foe = ChoiceView.creature(Names.foe(locale, opponent), opponent.creature().species(),
                    opponent.creature().level(), battle.tracker().condition(Battle.FOE_SIDE, foeLabel), false, locale);
        return new FieldView(battle.id(), battle.tracker().turn(), ally, foe);
    }

    public static FieldView of(ChoiceView view) {
        return new FieldView(view.battleId(), view.turn(), view.ally(), view.foe());
    }

    /**
     * Whether a creature is on the field yet.
     */
    public static boolean present(ChoiceView.Creature creature) {
        return !creature.name().isEmpty();
    }
}
