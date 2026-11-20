package com.astralrealms.poke.battle.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A simulator request, what a player has to answer: one active creature in singles, one {@link Slot} per position in
 * doubles (the answer is then one choice per slot, joined by {@code ", "}).
 *
 * @param waiting     nothing to choose: the opponent is choosing
 * @param forceSwitch an active creature fainted (or must leave): only switches are legal
 * @param trapped     the (first) active creature cannot switch out
 * @param reviving    Revival Blessing: the forced "switch" names a fainted member to bring back
 * @param moves       the (first) active creature's moves, empty for a forced switch
 * @param team        the side's creatures in their current order, the active ones first: {@code switch N} names a
 *                    position here
 * @param slots       each active position, in order ({@code a}, then {@code b}); one in singles
 */
public record ChoiceRequest(int rqid, boolean waiting, boolean forceSwitch, boolean trapped, boolean reviving,
                            List<MoveOption> moves, List<TeamMember> team, List<Slot> slots) {

    /** Showdown move targets the player picks in doubles; every other kind needs none. */
    private static final Set<String> CHOSEN_TARGETS = Set.of("normal", "any", "adjacentAlly", "adjacentAllyOrSelf",
            "adjacentFoe");

    public ChoiceRequest {
        moves = List.copyOf(moves);
        team = List.copyOf(team);
        slots = List.copyOf(slots);
    }

    /**
     * @param index    1-based, as {@code move N}
     * @param disabled no PP, disabled, or locked out by an item or a move
     * @param target   the move's Showdown target kind ({@code normal}, {@code self}, {@code allAdjacentFoes}...),
     *                 empty when the request does not say
     */
    public record MoveOption(int index, String id, int pp, int maxPp, boolean disabled, String target) {
    }

    /**
     * One active position and what its creature may do.
     *
     * @param index   0-based, {@code a} first
     * @param forced  it must be replaced (a forced switch request names it)
     * @param idle    it has nothing to do and passes: not named by a forced switch request, or fainted with nobody
     *                left to replace it
     * @param trapped it cannot switch out
     * @param moves   its moves, empty when it cannot move
     */
    public record Slot(int index, boolean forced, boolean idle, boolean trapped, List<MoveOption> moves) {

        public Slot {
            moves = List.copyOf(moves);
        }

        public boolean canUseMove(int index) {
            return !this.forced && !this.idle && this.moves.stream()
                    .anyMatch(move -> move.index() == index && !move.disabled());
        }

        public Optional<MoveOption> move(int index) {
            return this.moves.stream().filter(move -> move.index() == index).findFirst();
        }
    }

    /**
     * @param position 1-based, as {@code switch N}
     * @param label    the set name PokeBattle gave it
     */
    public record TeamMember(int position, String label, @Nullable Condition condition, boolean active) {

        public boolean fainted() {
            return this.condition != null && this.condition.fainted();
        }
    }

    /**
     * @throws IllegalArgumentException if the JSON is not a request
     */
    public static ChoiceRequest parse(String json) {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Not a request: " + json, e);
        }
        // A field of the wrong shape throws from the Gson getters (ClassCastException, IllegalStateException...)
        try {
            return read(root);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Malformed request: " + json, e);
        }
    }

    private static ChoiceRequest read(JsonObject root) {
        int rqid = root.has("rqid") ? root.get("rqid").getAsInt() : 0;
        boolean waiting = root.has("wait") && root.get("wait").getAsBoolean();
        boolean forceSwitch = false;
        List<Boolean> forced = new ArrayList<>();
        if (root.has("forceSwitch"))
            for (JsonElement element : root.getAsJsonArray("forceSwitch")) {
                forced.add(element.getAsBoolean());
                forceSwitch |= element.getAsBoolean();
            }

        List<List<MoveOption>> movesBySlot = new ArrayList<>();
        List<Boolean> trappedBySlot = new ArrayList<>();
        // A position with nobody on it (a side with fewer creatures than positions) comes as null
        List<Boolean> emptyBySlot = new ArrayList<>();
        if (!forceSwitch && root.has("active"))
            for (JsonElement element : root.getAsJsonArray("active")) {
                List<MoveOption> moves = new ArrayList<>();
                emptyBySlot.add(!element.isJsonObject());
                if (!element.isJsonObject()) {
                    movesBySlot.add(moves);
                    trappedBySlot.add(false);
                    continue;
                }
                JsonObject active = element.getAsJsonObject();
                trappedBySlot.add((active.has("trapped") && active.get("trapped").getAsBoolean())
                        || (active.has("maybeTrapped") && active.get("maybeTrapped").getAsBoolean()));
                JsonArray list = active.has("moves") ? active.getAsJsonArray("moves") : new JsonArray();
                for (int i = 0; i < list.size(); i++) {
                    JsonObject move = list.get(i).getAsJsonObject();
                    moves.add(new MoveOption(i + 1, string(move, "id"), integer(move, "pp", 0),
                            integer(move, "maxpp", 0), isDisabled(move), string(move, "target")));
                }
                movesBySlot.add(moves);
            }

        List<TeamMember> team = new ArrayList<>();
        List<Boolean> commanding = new ArrayList<>();
        boolean reviving = false;
        if (root.has("side")) {
            JsonArray list = root.getAsJsonObject("side").getAsJsonArray("pokemon");
            for (int i = 0; i < list.size(); i++) {
                JsonObject member = list.get(i).getAsJsonObject();
                boolean active = member.has("active") && member.get("active").getAsBoolean();
                reviving |= forceSwitch && active && member.has("reviving") && member.get("reviving").getAsBoolean();
                // Tatsugiri inside Dondozo: on the field, but it does nothing until it comes out
                commanding.add(active && member.has("commanding") && member.get("commanding").getAsBoolean());
                PokemonRef ref = PokemonRef.parse(string(member, "ident"));
                team.add(new TeamMember(i + 1, ref == null ? "" : ref.label(),
                        Condition.parse(string(member, "condition")), active));
            }
        }

        // One slot per active position: the forced switch flags, else the move requests
        int count = Math.max(1, forceSwitch ? forced.size() : movesBySlot.size());
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            boolean slotForced = forceSwitch && i < forced.size() && forced.get(i);
            // The active creatures come first in the team: a fainted one with nobody to replace it only passes, as
            // does a commanding one or an empty position
            boolean fainted = i < team.size() && team.get(i).active() && team.get(i).fainted();
            boolean empty = i >= team.size() || (i < emptyBySlot.size() && emptyBySlot.get(i));
            boolean idle = forceSwitch
                    ? !slotForced
                    : count > 1 && (fainted || empty || (i < commanding.size() && commanding.get(i)));
            slots.add(new Slot(i, slotForced, idle, i < trappedBySlot.size() && trappedBySlot.get(i),
                    forceSwitch || i >= movesBySlot.size() ? List.of() : movesBySlot.get(i)));
        }
        Slot first = slots.getFirst();
        return new ChoiceRequest(rqid, waiting, forceSwitch, first.trapped(), reviving, first.moves(), team, slots);
    }

    private static boolean isDisabled(JsonObject move) {
        if (!move.has("disabled"))
            return false;
        JsonElement disabled = move.get("disabled");
        // A string names the cause (an item or a move) and means disabled too
        return !disabled.isJsonPrimitive() || !disabled.getAsJsonPrimitive().isBoolean() || disabled.getAsBoolean();
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : fallback;
    }

    public boolean choosable() {
        return !this.waiting;
    }

    /**
     * Whether the side has more than one creature on the field: the answer is one choice per slot.
     */
    public boolean doubles() {
        return this.slots.size() > 1;
    }

    /**
     * Whether a move of this target kind needs the player to pick its target ({@code move N T}): only in doubles.
     */
    public static boolean targeted(String target, int actives) {
        return actives > 1 && CHOSEN_TARGETS.contains(target);
    }

    /**
     * The target locations a move of this kind may be aimed at from {@code slot}, as Showdown numbers them: the
     * opponent's positions from 1 ({@code 1} is {@code p2a}), the side's own as negatives ({@code -1} is its
     * {@code a}). Every position is adjacent to every other in doubles. Empty when the move takes no target.
     */
    public static List<Integer> targets(String target, int slot, int actives) {
        if (!targeted(target, actives))
            return List.of();
        List<Integer> locations = new ArrayList<>();
        boolean foes = !target.startsWith("adjacentAlly");
        boolean allies = !target.equals("adjacentFoe");
        if (foes)
            for (int i = 1; i <= actives; i++)
                locations.add(i);
        if (allies)
            for (int i = 0; i < actives; i++)
                if (i != slot || target.equals("adjacentAllyOrSelf"))
                    locations.add(-(i + 1));
        return locations;
    }

    public Optional<TeamMember> active() {
        return this.team.stream().filter(TeamMember::active).findFirst();
    }

    /**
     * Whether the creature at this position can be sent in: on the bench and still standing. After Revival Blessing,
     * the opposite: a fainted one, to bring back.
     */
    public boolean canSwitchTo(int position) {
        if (this.trapped && !this.forceSwitch)
            return false;
        return this.onBench(position);
    }

    /**
     * Whether the creature at this position is one a switch may send in, whoever leaves for it (in doubles, each
     * slot is trapped or not on its own): on the bench and still standing. After Revival Blessing, any fainted
     * member, a fainted partner still on the field included (doubles).
     */
    public boolean onBench(int position) {
        return this.team.stream()
                .anyMatch(member -> member.position() == position
                        && (this.reviving ? member.fainted() : !member.active() && !member.fainted()));
    }

    /**
     * Whether the active creature can act otherwise than by a move (ball, bag, flee): a move request, not a forced
     * switch, in singles (the worker's PokeSMP choices take a whole side's turn, so doubles have none).
     */
    public boolean canAct() {
        return !this.waiting && !this.forceSwitch && !this.moves.isEmpty() && !this.doubles();
    }

    public boolean canUseMove(int index) {
        // With every move out of PP, the simulator lists Struggle alone, as move 1
        if (this.forceSwitch)
            return false;
        return this.moves.stream().anyMatch(move -> move.index() == index && !move.disabled());
    }
}
