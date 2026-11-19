package com.astralrealms.poke.battle.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DoublesChoiceTest {

    private static final UUID BATTLE = UUID.randomUUID();
    private static final ChoiceView.Creature EMBERLING = creature("Emberling", 30, 38);
    private static final ChoiceView.Creature LEAFKIT = creature("Leafkit", 21, 31);
    private static final ChoiceView.Creature PEBBLIT = creature("Pebblit", 70, 100);
    private static final ChoiceView.Creature MOSSLET = creature("Mosslet", 40, 100);
    private static final ChoiceView.Move EMBER_TAP = move(1, "normal");
    private static final ChoiceView.Move HEAT_RING = move(2, "allAdjacentFoes");
    private static final ChoiceView.Move LEAF_CUT = move(1, "any");
    private static final ChoiceView.Member THIRD = member(3);
    private static final ChoiceView.Member FOURTH = member(4);

    private static ChoiceView.Creature creature(String name, int hp, int max) {
        return new ChoiceView.Creature(name, 10, hp, max, "", "", name.toLowerCase());
    }

    private static ChoiceView.Move move(int index, String target) {
        return new ChoiceView.Move(index, "move" + index, "", "", "", 40, 100, 10, 10, false, target);
    }

    private static ChoiceView.Member member(int position) {
        return new ChoiceView.Member(position, "member" + position, 10, 20, 20, "", false);
    }

    private static ChoiceView.SlotView moving(int index, ChoiceView.Move... moves) {
        return new ChoiceView.SlotView(index, false, false, false, List.of(moves));
    }

    private static ChoiceView.SlotView forced(int index, boolean forced) {
        return new ChoiceView.SlotView(index, forced, !forced, false, List.of());
    }

    private static ChoiceView view(List<ChoiceView.SlotView> slots, List<ChoiceView.Creature> foes,
                                   List<ChoiceView.Member> bench) {
        return ChoiceView.doubles(BATTLE, 7, 3, slots, List.of(EMBERLING, LEAFKIT), foes, bench, bench, 1000L);
    }

    @Test
    void eachCreatureChoosesInTurnThenOneAnswerIsSent() {
        ChoiceView first = view(List.of(moving(0, EMBER_TAP, HEAT_RING), moving(1, LEAF_CUT)),
                List.of(PEBBLIT, MOSSLET), List.of(THIRD));
        assertThat(first).isNotNull();
        assertThat(first.doubles().slot()).isZero();
        assertThat(first.ally()).isEqualTo(EMBERLING);
        assertThat(first.moves()).containsExactly(EMBER_TAP, HEAT_RING);

        // A targeted move is aimed at either foe or the partner, a spread move at nobody
        assertThat(first.targets(EMBER_TAP)).extracting(ChoiceView.Target::location).containsExactly(1, 2, -2);
        assertThat(first.targets(EMBER_TAP).getFirst().creature()).isEqualTo(PEBBLIT);
        assertThat(first.targets(EMBER_TAP).getLast().creature()).isEqualTo(LEAFKIT);
        assertThat(first.targets(HEAT_RING)).isEmpty();

        ChoiceView second = first.next("move 1 2");
        assertThat(second).isNotNull();
        assertThat(second.doubles().slot()).isEqualTo(1);
        assertThat(second.ally()).isEqualTo(LEAFKIT);
        assertThat(second.doubles().previous()).isSameAs(first);
        assertThat(second.askedAt()).isEqualTo(first.askedAt());
        assertThat(second.targets(LEAF_CUT)).extracting(ChoiceView.Target::location).containsExactly(1, 2, -1);

        assertThat(second.next("move 1 -1")).isNull();
        assertThat(second.choice("move 1 -1")).isEqualTo("move 1 2, move 1 -1");
    }

    @Test
    void aBenchMemberPickedForOneSlotIsNotOfferedToTheOther() {
        ChoiceView first = view(List.of(moving(0, EMBER_TAP), moving(1, LEAF_CUT)), List.of(PEBBLIT, MOSSLET),
                List.of(THIRD, FOURTH));
        assertThat(first.switchable()).containsExactly(THIRD, FOURTH);
        ChoiceView second = first.next("switch 3");
        assertThat(second.switchable()).containsExactly(FOURTH);
        assertThat(second.choice("switch 4")).isEqualTo("switch 3, switch 4");
    }

    @Test
    void forcedSwitchesPassWhenNobodyIsLeft() {
        ChoiceView both = view(List.of(forced(0, true), forced(1, true)), List.of(PEBBLIT, MOSSLET), List.of(THIRD));
        assertThat(both.forceSwitch()).isTrue();
        assertThat(both.next("switch 3")).isNull();
        assertThat(both.choice("switch 3")).isEqualTo("switch 3, pass");

        // Only the second slot was knocked out: the first passes and is never asked
        ChoiceView second = view(List.of(forced(0, false), forced(1, true)), List.of(PEBBLIT, MOSSLET),
                List.of(THIRD));
        assertThat(second.doubles().slot()).isEqualTo(1);
        assertThat(second.ally()).isEqualTo(LEAFKIT);
        assertThat(second.doubles().previous()).isNull();
        assertThat(second.choice("switch 3")).isEqualTo("pass, switch 3");
    }

    @Test
    void aFaintedFoeIsNotAimedAtWhileTheOtherStands() {
        ChoiceView.Creature fainted = creature("Mosslet", 0, 100);
        ChoiceView first = view(List.of(moving(0, EMBER_TAP), moving(1, LEAF_CUT)), List.of(PEBBLIT, fainted),
                List.of());
        assertThat(first.targets(EMBER_TAP)).extracting(ChoiceView.Target::location).containsExactly(1, -2);
    }

    @Test
    void singlesSendTheChoiceAsItIs() {
        ChoiceView singles = new ChoiceView(BATTLE, 2, 1, false, false, EMBERLING, PEBBLIT, List.of(EMBER_TAP),
                List.of(), List.of(), ChoiceView.Actions.NONE, 1000L, null);
        assertThat(singles.next("move 1")).isNull();
        assertThat(singles.choice("move 1")).isEqualTo("move 1");
        assertThat(singles.targets(EMBER_TAP)).isEmpty();
        assertThat(FieldView.of(singles).doubles()).isFalse();
    }
}
