package com.astralrealms.poke.battle.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.astralrealms.poke.battle.ui.ChoiceView;
import com.astralrealms.poke.core.mechanics.item.ItemEffect;

import net.kyori.adventure.text.Component;

class BattleBagTest {

    private static ItemEffect effect(ItemEffect.Type type, Integer amount, Double fraction, boolean cureStatus) {
        return new ItemEffect(type, amount, fraction, null, null, null, null, cureStatus);
    }

    private static final ItemEffect HEAL_20 = effect(ItemEffect.Type.HEAL, 20, null, false);
    private static final ItemEffect FULL_RESTORE = effect(ItemEffect.Type.HEAL, null, null, true);
    private static final ItemEffect REVIVE = effect(ItemEffect.Type.REVIVE, null, null, false);
    private static final ItemEffect CURE = effect(ItemEffect.Type.CURE_STATUS, null, null, false);
    private static final ItemEffect PP_10 = effect(ItemEffect.Type.RESTORE_PP, 10, null, false);

    private static ChoiceView.Member member(int position, int hp, int max, String status) {
        return new ChoiceView.Member(position, "m" + position, 10, hp, max, status, hp == 0);
    }

    private static ChoiceView.Member member(int position, int hp, String statusId, boolean active) {
        return new ChoiceView.Member(position, "m" + position, 10, hp, 30, statusId.isEmpty() ? "" : "S " + statusId,
                hp == 0, List.of(), "", "", 0, "", active, statusId);
    }

    private static ItemEffect boost(String stat, Integer stages) {
        return new ItemEffect(ItemEffect.Type.BOOST, null, null, null, null, null, null, false, stat, stages, null,
                false);
    }

    @Test
    void encodesEffectsAsTheWorkerReadsThem() {
        assertThat(BattleBag.encode(List.of(HEAL_20))).isEqualTo("heal=20");
        assertThat(BattleBag.encode(List.of(FULL_RESTORE))).isEqualTo("heal+cure");
        assertThat(BattleBag.encode(List.of(REVIVE))).isEqualTo("revive=0.5");
        assertThat(BattleBag.encode(List.of(effect(ItemEffect.Type.REVIVE, null, 1.0, false)))).isEqualTo("revive=1");
        assertThat(BattleBag.encode(List.of(CURE, FULL_RESTORE))).isEqualTo("cure+heal");
        assertThat(BattleBag.encode(List.of(PP_10, effect(ItemEffect.Type.RESTORE_PP, null, null, false))))
                .isEqualTo("pp=10+pp");
    }

    @Test
    void encodesBerryAndXItemEffects() {
        assertThat(BattleBag.encode(List.of(effect(ItemEffect.Type.HEAL, null, 0.25, false)))).isEqualTo("healf=0.25");
        assertThat(BattleBag.encode(List.of(new ItemEffect(ItemEffect.Type.CURE_STATUS, null, null, null, null, null,
                null, false, null, null, List.of("psn", "tox"), false)))).isEqualTo("cure=psn.tox");
        assertThat(BattleBag.encode(List.of(new ItemEffect(ItemEffect.Type.RESTORE_PP, 10, null, null, null, null,
                null, false, null, null, null, true)))).isEqualTo("pp1=10");
        assertThat(BattleBag.encode(List.of(boost("atk", 2)))).isEqualTo("boost=atk:2");
    }

    @Test
    void keepsOnlyValidBoosts() {
        assertThat(BattleBag.battleEffects(List.of(boost("atk", 2), boost("hp", 2), boost("spe", 7), boost(null, 1))))
                .containsExactly(boost("atk", 2));
    }

    @Test
    void offersBoostsToTheActiveCreatureAndStatusBerriesToTheirStatus() {
        List<ChoiceView.Member> team = List.of(
                member(1, 30, "par", true),
                member(2, 12, "brn", false),
                member(3, 0, "", false));
        ItemEffect cheri = new ItemEffect(ItemEffect.Type.CURE_STATUS, null, null, null, null, null, null, false,
                null, null, List.of("par"), false);

        assertThat(targets(boost("def", 2), team)).containsExactly(1);
        assertThat(targets(cheri, team)).containsExactly(1);
    }

    @Test
    void leavesOutEffectsTheSimulatorCannotApply() {
        ItemEffect levelUp = effect(ItemEffect.Type.LEVEL_UP, null, null, false);
        assertThat(BattleBag.battleEffects(List.of(levelUp))).isEmpty();
        assertThat(BattleBag.battleEffects(List.of(levelUp, HEAL_20))).containsExactly(HEAL_20);
    }

    @Test
    void offersAnItemOnlyToMembersItWouldChange() {
        List<ChoiceView.Member> team = List.of(
                member(1, 30, 30, ""),
                member(2, 12, 30, ""),
                member(3, 30, 30, "Brûlure"),
                member(4, 0, 30, ""));

        assertThat(targets(HEAL_20, team)).containsExactly(2);
        assertThat(targets(FULL_RESTORE, team)).containsExactly(2, 3);
        assertThat(targets(CURE, team)).containsExactly(3);
        assertThat(targets(REVIVE, team)).containsExactly(4);
        // PP are only known for the active creature: offered to every standing member
        assertThat(targets(PP_10, team)).containsExactly(1, 2, 3);
    }

    private static List<Integer> targets(ItemEffect effect, List<ChoiceView.Member> team) {
        BattleBag.Entry entry = new BattleBag.Entry("x", Component.empty(), 1, List.of(effect), null);
        return BattleBag.targets(entry, team).stream().map(ChoiceView.Member::position).toList();
    }
}
