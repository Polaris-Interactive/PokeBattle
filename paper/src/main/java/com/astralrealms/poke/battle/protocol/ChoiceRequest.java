package com.astralrealms.poke.battle.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A simulator request, what a player has to answer. Singles only for now (one active creature).
 *
 * @param waiting     nothing to choose: the opponent is choosing
 * @param forceSwitch the active creature fainted (or must leave): only a switch is legal
 * @param trapped     the active creature cannot switch out
 * @param moves       the active creature's moves, empty for a forced switch
 * @param team        the side's creatures in their current order: {@code switch N} names a position here
 */
public record ChoiceRequest(int rqid, boolean waiting, boolean forceSwitch, boolean trapped, List<MoveOption> moves,
                            List<TeamMember> team) {

    public ChoiceRequest {
        moves = List.copyOf(moves);
        team = List.copyOf(team);
    }

    /**
     * @param index    1-based, as {@code move N}
     * @param disabled no PP, disabled, or locked out by an item or a move
     */
    public record MoveOption(int index, String id, int pp, int maxPp, boolean disabled) {
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

        int rqid = root.has("rqid") ? root.get("rqid").getAsInt() : 0;
        boolean waiting = root.has("wait") && root.get("wait").getAsBoolean();
        boolean forceSwitch = false;
        if (root.has("forceSwitch"))
            for (JsonElement element : root.getAsJsonArray("forceSwitch"))
                forceSwitch |= element.getAsBoolean();

        List<MoveOption> moves = new ArrayList<>();
        boolean trapped = false;
        if (!forceSwitch && root.has("active")) {
            JsonObject active = root.getAsJsonArray("active").get(0).getAsJsonObject();
            trapped = (active.has("trapped") && active.get("trapped").getAsBoolean())
                    || (active.has("maybeTrapped") && active.get("maybeTrapped").getAsBoolean());
            JsonArray list = active.getAsJsonArray("moves");
            for (int i = 0; i < list.size(); i++) {
                JsonObject move = list.get(i).getAsJsonObject();
                moves.add(new MoveOption(i + 1, string(move, "id"), integer(move, "pp", 0),
                        integer(move, "maxpp", 0), isDisabled(move)));
            }
        }

        List<TeamMember> team = new ArrayList<>();
        if (root.has("side")) {
            JsonArray list = root.getAsJsonObject("side").getAsJsonArray("pokemon");
            for (int i = 0; i < list.size(); i++) {
                JsonObject member = list.get(i).getAsJsonObject();
                PokemonRef ref = PokemonRef.parse(string(member, "ident"));
                team.add(new TeamMember(i + 1, ref == null ? "" : ref.label(),
                        Condition.parse(string(member, "condition")),
                        member.has("active") && member.get("active").getAsBoolean()));
            }
        }
        return new ChoiceRequest(rqid, waiting, forceSwitch, trapped, moves, team);
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

    public Optional<TeamMember> active() {
        return this.team.stream().filter(TeamMember::active).findFirst();
    }

    /**
     * Whether the creature at this position can be sent in: on the bench and still standing.
     */
    public boolean canSwitchTo(int position) {
        if (this.trapped && !this.forceSwitch)
            return false;
        return this.team.stream()
                .anyMatch(member -> member.position() == position && !member.active() && !member.fainted());
    }

    public boolean canUseMove(int index) {
        // With every move out of PP, the simulator lists Struggle alone, as move 1
        if (this.forceSwitch)
            return false;
        return this.moves.stream().anyMatch(move -> move.index() == index && !move.disabled());
    }
}
