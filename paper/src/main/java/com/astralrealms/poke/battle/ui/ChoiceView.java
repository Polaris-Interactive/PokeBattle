package com.astralrealms.poke.battle.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.battle.model.Battle;
import com.astralrealms.poke.battle.model.Names;
import com.astralrealms.poke.battle.protocol.ChoiceRequest;
import com.astralrealms.poke.battle.protocol.Condition;
import com.astralrealms.poke.battle.protocol.SlotChoices;
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
 * @param doubles    in doubles, which slot this view chooses for and what was chosen before it; null in singles.
 *                   {@code forceSwitch}, {@code trapped}, {@code ally}, {@code moves} and {@code switchable} are then
 *                   those of that slot
 */
public record ChoiceView(UUID battleId, int rqid, int turn, boolean forceSwitch, boolean trapped, Creature ally,
                         Creature foe, List<Move> moves, List<Member> switchable, List<Member> team,
                         Actions actions, long askedAt, @Nullable Doubles doubles) {

    private static final Creature NOBODY = new Creature("", 0, 0, 0, "", "", "");

    public ChoiceView {
        moves = List.copyOf(moves);
        switchable = List.copyOf(switchable);
        team = List.copyOf(team);
    }

    /**
     * A doubles choice is made one slot after the other, then sent as one answer ({@link SlotChoices}): this is where
     * it stands.
     *
     * @param slot     the slot this view chooses for, 0-based
     * @param slots    every active slot of the request
     * @param picks    the choices already made for the slots before {@code slot} ({@code pass} for those with nothing
     *                 to do)
     * @param allies   the player's creatures on the field, by slot
     * @param foes     the opponent's, by slot
     * @param bench    the members a switch could send in, before any pick
     * @param previous the view of the slot chosen before this one, to go back to; null for the first
     */
    public record Doubles(int slot, List<SlotView> slots, List<String> picks, List<Creature> allies,
                          List<Creature> foes, List<Member> bench, @Nullable ChoiceView previous) {

        public Doubles {
            slots = List.copyOf(slots);
            picks = List.copyOf(picks);
            allies = List.copyOf(allies);
            foes = List.copyOf(foes);
            bench = List.copyOf(bench);
        }
    }

    /**
     * One active slot of a doubles request.
     *
     * @param idle nothing to do there: it passes
     */
    public record SlotView(int index, boolean forced, boolean idle, boolean trapped, List<Move> moves) {

        public SlotView {
            moves = List.copyOf(moves);
        }
    }

    /**
     * A creature a move can be aimed at.
     *
     * @param location as Showdown numbers it: the opponent's positions from 1, the player's own as negatives
     * @param foe      whether it is the opponent's
     */
    public record Target(int location, Creature creature, boolean foe) {
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
     * @param target   its Showdown target kind ({@code normal}...), empty when unknown
     */
    public record Move(int index, String name, String type, String typeId, String category, int power,
                       @Nullable Integer accuracy, int pp, int maxPp, boolean disabled, String target) {
    }

    /**
     * @param position as {@code switch N} (and the target of a bag item)
     * @param status   display name, empty when healthy
     * @param moves    its moves with the PP stored before the battle; the active's with the PP the request tells
     * @param ability            display name, empty when unknown
     * @param abilityDescription what the ability does, empty when unknown
     * @param speed              its Speed stat, 0 when unknown
     * @param species            its species id, for its icon; empty when unknown
     * @param active             whether it is the one on the field
     * @param statusId           the status as Showdown names it (brn, par, ...), empty when healthy
     */
    public record Member(int position, String name, int level, int hp, int maxHp, String status, boolean fainted,
                         List<MemberMove> moves, String ability, String abilityDescription, int speed, String species,
                         boolean active, String statusId) {

        public Member {
            moves = List.copyOf(moves);
        }

        public Member(int position, String name, int level, int hp, int maxHp, String status, boolean fainted) {
            this(position, name, level, hp, maxHp, status, fainted, List.of(), "", "", 0, "", false, "");
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

        // The player's creatures on the field, by slot (they come first in the request's team)
        List<Creature> allies = new ArrayList<>();
        for (int slot = 0; slot < request.slots().size(); slot++) {
            ChoiceRequest.TeamMember active = slot < request.team().size() && request.team().get(slot).active()
                    ? request.team().get(slot)
                    : null;
            if (!request.doubles() && active == null)
                active = request.active().orElse(null);
            Battle.Ally member = active == null ? null : battle.ally(active.label()).orElse(null);
            allies.add(member == null ? NOBODY : creature(Names.ally(locale, member), member.pokemon().species(),
                    member.pokemon().level(), active.condition(), true, locale));
        }

        List<Creature> foes = new ArrayList<>();
        for (int slot = 0; slot < Math.max(1, battle.tracker().slots()); slot++) {
            String foeLabel = battle.tracker().active(Battle.FOE_SIDE, slot);
            Battle.Foe opponent = foeLabel == null ? null : battle.foe(foeLabel).orElse(null);
            foes.add(opponent == null ? NOBODY : creature(Names.foe(locale, opponent), opponent.species(),
                    opponent.level(), battle.tracker().condition(Battle.FOE_SIDE, foeLabel), false, locale));
        }

        List<SlotView> slots = new ArrayList<>();
        for (ChoiceRequest.Slot slot : request.slots())
            slots.add(new SlotView(slot.index(), slot.forced(), slot.idle(), slot.trapped(),
                    moves(slot.moves(), registry, locale)));

        List<Member> switchable = new ArrayList<>();
        List<Member> team = new ArrayList<>();
        for (ChoiceRequest.TeamMember member : request.team()) {
            Battle.Ally stored = battle.ally(member.label()).orElse(null);
            if (stored == null)
                continue;
            Condition condition = member.condition();
            Pokemon pokemon = stored.pokemon();
            // The request tells the PP of the creatures on the field (the first ones of the team, by slot)
            List<ChoiceRequest.MoveOption> asked = !member.active() || member.position() > request.slots().size()
                    ? List.of()
                    : request.slots().get(member.position() - 1).moves();
            List<MemberMove> known = new ArrayList<>();
            for (MoveSlot slot : pokemon.moves()) {
                MoveData data = registry.findMove(slot.move()).orElse(null);
                // The others have not moved since the battle began
                ChoiceRequest.MoveOption option = asked.stream()
                        .filter(move -> move.id().equals(slot.move()))
                        .findFirst()
                        .orElse(null);
                known.add(new MemberMove(Names.text(locale, "move", slot.move()),
                        data == null ? "" : typeId(data.type()), option == null ? slot.pp() : option.pp(),
                        option != null ? option.maxPp() : data == null ? slot.pp() : data.maxPp(slot.ppUps())));
            }
            Member view = new Member(member.position(), Names.ally(locale, stored), pokemon.level(),
                    condition == null ? 0 : condition.hp(), condition == null ? 0 : condition.maxHp(),
                    status(condition, locale), member.fainted(), known,
                    pokemon.ability() == null ? "" : Names.text(locale, "ability", pokemon.ability()),
                    pokemon.ability() == null ? "" : Names.description(locale, "ability", pokemon.ability()),
                    speed(pokemon, registry), pokemon.species(), member.active(), statusId(condition));
            team.add(view);
            if (request.doubles() ? request.onBench(member.position()) : request.canSwitchTo(member.position()))
                switchable.add(view);
        }

        Actions actions = Actions.NONE;
        if (request.canAct()) {
            Battle.Options options = battle.options();
            actions = new Actions(options.capture() && options.wildRoll() != null, options.bag(), options.flee());
        }
        long askedAt = System.currentTimeMillis();
        if (!request.doubles())
            return new ChoiceView(battle.id(), request.rqid(), battle.tracker().turn(), request.forceSwitch(),
                    request.trapped(), allies.getFirst(), foes.getFirst(), slots.getFirst().moves(), switchable, team,
                    actions, askedAt, null);

        // Doubles: the first slot with something to do, those before it passing
        ChoiceView start = new ChoiceView(battle.id(), request.rqid(), battle.tracker().turn(), request.forceSwitch(),
                false, allies.getFirst(), foes.getFirst(), List.of(), switchable, team, actions, askedAt,
                new Doubles(-1, slots, List.of(), allies, foes, switchable, null));
        return start.following(List.of(), null);
    }

    private static List<Move> moves(List<ChoiceRequest.MoveOption> options, SpeciesRegistry registry,
                                    @Nullable Locale locale) {
        List<Move> moves = new ArrayList<>();
        for (ChoiceRequest.MoveOption option : options) {
            MoveData data = registry.findMove(option.id()).orElse(null);
            moves.add(new Move(option.index(), Names.text(locale, "move", option.id()),
                    data == null ? "" : Names.text(locale, "type", data.type()),
                    data == null ? "" : typeId(data.type()),
                    data == null ? "" : Names.text(locale, "category", data.category().name().toLowerCase(Locale.ROOT)),
                    data == null ? 0 : data.power(), data == null ? null : data.accuracy(),
                    option.pp(), option.maxPp(), option.disabled(), option.target()));
        }
        return moves;
    }

    // ---- Doubles ----

    /**
     * The view of the slot to choose for after this one, given its {@code pick}; null when every slot is chosen
     * (send {@link #choice}). Always null in singles.
     */
    public @Nullable ChoiceView next(String pick) {
        if (this.doubles == null)
            return null;
        List<String> picks = new ArrayList<>(this.doubles.picks());
        picks.add(pick);
        return this.following(picks, this);
    }

    /**
     * The answer to send once this view's slot is chosen with {@code pick}: every slot's choice, those left passing.
     * The pick itself in singles.
     */
    public String choice(String pick) {
        if (this.doubles == null)
            return pick;
        List<String> parts = new ArrayList<>(this.doubles.picks());
        parts.add(pick);
        while (parts.size() < this.doubles.slots().size())
            parts.add(SlotChoices.PASS);
        return SlotChoices.join(parts);
    }

    /**
     * What a move of this view's slot can be aimed at: empty when it takes no target (singles, a move hitting every
     * foe, one on itself...). Creatures no longer standing are left out while another one can be aimed at.
     */
    public List<Target> targets(Move move) {
        if (this.doubles == null)
            return List.of();
        int actives = this.doubles.slots().size();
        List<Target> all = new ArrayList<>();
        for (int location : ChoiceRequest.targets(move.target(), this.doubles.slot(), actives)) {
            boolean foe = location > 0;
            List<Creature> side = foe ? this.doubles.foes() : this.doubles.allies();
            int index = Math.abs(location) - 1;
            all.add(new Target(location, index < side.size() ? side.get(index) : NOBODY, foe));
        }
        List<Target> standing = all.stream()
                .filter(target -> FieldView.present(target.creature()) && target.creature().hp() > 0)
                .toList();
        return standing.isEmpty() ? all : standing;
    }

    /**
     * The first slot after this view's that has something to do, with {@code picks} made before it and passes
     * added for the slots skipped; null when none is left.
     */
    private @Nullable ChoiceView following(List<String> picks, @Nullable ChoiceView previous) {
        Doubles doubles = this.doubles;
        if (doubles == null)
            return null;
        List<String> made = new ArrayList<>(picks);
        for (int index = doubles.slot() + 1; index < doubles.slots().size(); index++) {
            SlotView slot = doubles.slots().get(index);
            List<Member> left = remaining(doubles.bench(), made);
            if (slot.idle() || (slot.forced() && left.isEmpty())) {
                made.add(SlotChoices.PASS);
                continue;
            }
            Creature ally = index < doubles.allies().size() ? doubles.allies().get(index) : NOBODY;
            return new ChoiceView(this.battleId, this.rqid, this.turn, slot.forced(), slot.trapped(), ally, this.foe,
                    slot.moves(), left, this.team, this.actions, this.askedAt,
                    new Doubles(index, doubles.slots(), made, doubles.allies(), doubles.foes(), doubles.bench(),
                            previous));
        }
        return null;
    }

    /**
     * The bench members no earlier pick sends in.
     */
    private static List<Member> remaining(List<Member> bench, List<String> picks) {
        Set<Integer> taken = new HashSet<>();
        for (String pick : picks)
            taken.add(SlotChoices.switched(pick));
        return bench.stream().filter(member -> !taken.contains(member.position())).toList();
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
