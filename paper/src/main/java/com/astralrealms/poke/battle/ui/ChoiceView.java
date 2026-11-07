package com.astralrealms.poke.battle.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.battle.model.Battle;
import com.astralrealms.poke.battle.model.Names;
import com.astralrealms.poke.battle.protocol.ChoiceRequest;
import com.astralrealms.poke.battle.protocol.Condition;
import com.astralrealms.poke.core.PokeCoreAPI;
import com.astralrealms.poke.core.mechanics.PokemonStats;
import com.astralrealms.poke.core.model.MoveSlot;
import com.astralrealms.poke.core.model.Pokemon;
import com.astralrealms.poke.core.species.MoveData;
import com.astralrealms.poke.core.species.SpeciesRegistry;

/**
 * What the choice dialog shows, taken from a battle under its monitor: the dialog is then built on the player's
 * thread from this snapshot alone.
 *
 * @param rqid       the request the buttons answer; a click for an older one is refused
 * @param switchable members a switch can send in
 * @param team       every member in request order, fainted ones included (bag targets)
 * @param actions    what the player may do besides moves and switches this turn
 * @param askedAt    when it was asked (epoch ms): the turn timer counts from there, not from when the UI shows it
 *                   (after the turn's beats have played)
 */
public record ChoiceView(UUID battleId, int rqid, int turn, boolean forceSwitch, boolean trapped, Creature ally,
                         Creature foe, List<Move> moves, List<Member> switchable, List<Member> team,
                         Actions actions, long askedAt) {

    public ChoiceView {
        moves = List.copyOf(moves);
        switchable = List.copyOf(switchable);
        team = List.copyOf(team);
    }

    /**
     * Ball, bag and flee buttons: allowed by the battle, and only when the active creature acts (not a forced switch).
     */
    public record Actions(boolean capture, boolean bag, boolean flee) {

        public static final Actions NONE = new Actions(false, false, false);
    }

    /**
     * @param hp      exact HP for the player's creature, a percentage for the opponent
     * @param status  display name, empty when healthy
     * @param statusId the status as Showdown names it (brn, par, ...), empty when healthy
     */
    /**
     * @param species its species id ("" when nobody is there), for the creature standing on the stage
     */
    public record Creature(String name, int level, int hp, int maxHp, String status, String statusId, String species) {
    }

    /**
     * @param type     display name
     * @param typeId   the type's id ({@code grass}), empty when unknown
     * @param power    0 for status moves
     * @param accuracy null when it never misses
     */
    public record Move(int index, String name, String type, String typeId, String category, int power,
                       @Nullable Integer accuracy, int pp, int maxPp, boolean disabled) {
    }

    /**
     * @param position as {@code switch N} (and the target of a bag item)
     * @param status   display name, empty when healthy
     * @param moves    its moves with the PP stored before the battle (the request only has the active's PP)
     * @param ability  display name, empty when unknown
     * @param speed    its Speed stat, 0 when unknown
     */
    public record Member(int position, String name, int level, int hp, int maxHp, String status, boolean fainted,
                         List<MemberMove> moves, String ability, int speed) {

        public Member {
            moves = List.copyOf(moves);
        }

        public Member(int position, String name, int level, int hp, int maxHp, String status, boolean fainted) {
            this(position, name, level, hp, maxHp, status, fainted, List.of(), "", 0);
        }
    }

    /**
     * @param typeId the type's id ({@code grass}), empty when unknown
     */
    public record MemberMove(String name, String typeId, int pp, int maxPp) {
    }

    /**
     * Must run under the battle's monitor.
     */
    public static @Nullable ChoiceView of(Battle battle, @Nullable Locale locale) {
        ChoiceRequest request = battle.pending();
        if (request == null || !request.choosable())
            return null;
        SpeciesRegistry registry = PokeCoreAPI.get().species();

        Creature ally = new Creature("", 0, 0, 0, "", "", "");
        ChoiceRequest.TeamMember active = request.active().orElse(null);
        if (active != null) {
            Battle.Ally member = battle.ally(active.label()).orElse(null);
            if (member != null)
                ally = creature(Names.ally(locale, member), member.pokemon().species(), member.pokemon().level(),
                        active.condition(), true, locale);
        }

        Creature foe = new Creature("", 0, 0, 0, "", "", "");
        String foeLabel = battle.tracker().active(Battle.FOE_SIDE);
        Battle.Foe opponent = foeLabel == null ? null : battle.foe(foeLabel).orElse(null);
        if (opponent != null)
            foe = creature(Names.foe(locale, opponent), opponent.creature().species(),
                    opponent.creature().level(), battle.tracker().condition(Battle.FOE_SIDE, foeLabel), false, locale);

        List<Move> moves = new ArrayList<>();
        for (ChoiceRequest.MoveOption option : request.moves()) {
            MoveData data = registry.findMove(option.id()).orElse(null);
            moves.add(new Move(option.index(), Names.text(locale, "move", option.id()),
                    data == null ? "" : Names.text(locale, "type", data.type()),
                    data == null ? "" : typeId(data.type()),
                    data == null ? "" : Names.text(locale, "category", data.category().name().toLowerCase(Locale.ROOT)),
                    data == null ? 0 : data.power(), data == null ? null : data.accuracy(),
                    option.pp(), option.maxPp(), option.disabled()));
        }

        List<Member> switchable = new ArrayList<>();
        List<Member> team = new ArrayList<>();
        for (ChoiceRequest.TeamMember member : request.team()) {
            Battle.Ally stored = battle.ally(member.label()).orElse(null);
            if (stored == null)
                continue;
            Condition condition = member.condition();
            Pokemon pokemon = stored.pokemon();
            List<MemberMove> known = new ArrayList<>();
            for (MoveSlot slot : pokemon.moves()) {
                MoveData data = registry.findMove(slot.move()).orElse(null);
                known.add(new MemberMove(Names.text(locale, "move", slot.move()),
                        data == null ? "" : typeId(data.type()), slot.pp(),
                        data == null ? slot.pp() : data.maxPp(slot.ppUps())));
            }
            Member view = new Member(member.position(), Names.ally(locale, stored), pokemon.level(),
                    condition == null ? 0 : condition.hp(), condition == null ? 0 : condition.maxHp(),
                    status(condition, locale), member.fainted(), known,
                    pokemon.ability() == null ? "" : Names.text(locale, "ability", pokemon.ability()),
                    speed(pokemon, registry));
            team.add(view);
            if (request.canSwitchTo(member.position()))
                switchable.add(view);
        }

        Actions actions = Actions.NONE;
        if (request.canAct()) {
            Battle.Options options = battle.options();
            actions = new Actions(options.capture() && options.wildRoll() != null, options.bag(), options.flee());
        }
        return new ChoiceView(battle.id(), request.rqid(), battle.tracker().turn(), request.forceSwitch(),
                request.trapped(), ally, foe, moves, switchable, team, actions, System.currentTimeMillis());
    }

    /**
     * @param exact the player's own creature (exact HP); the opponent's is only known as a percentage
     */
    static Creature creature(String name, String species, int level, @Nullable Condition condition, boolean exact,
                             @Nullable Locale locale) {
        if (condition == null)
            return new Creature(name, level, 0, 0, "", "", species);
        return new Creature(name, level, exact ? condition.hp() : condition.percent(),
                exact ? condition.maxHp() : 100, status(condition, locale), statusId(condition), species);
    }

    private static String typeId(String type) {
        return type.toLowerCase(Locale.ROOT);
    }

    private static int speed(Pokemon pokemon, SpeciesRegistry registry) {
        try {
            return PokemonStats.of(pokemon, registry).spe();
        } catch (RuntimeException e) {
            // A species missing from the bundle: the tooltip just leaves the Speed out
            return 0;
        }
    }

    private static String statusId(@Nullable Condition condition) {
        if (condition == null || condition.status().isEmpty() || condition.fainted())
            return "";
        return condition.status();
    }

    private static String status(@Nullable Condition condition, @Nullable Locale locale) {
        if (condition == null || condition.status().isEmpty() || condition.fainted())
            return "";
        return Names.text(locale, "status", condition.status());
    }
}
