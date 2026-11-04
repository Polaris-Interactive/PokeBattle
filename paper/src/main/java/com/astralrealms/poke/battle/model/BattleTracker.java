package com.astralrealms.poke.battle.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.battle.protocol.Condition;
import com.astralrealms.poke.battle.protocol.PokemonRef;
import com.astralrealms.poke.battle.protocol.ProtocolLine;

/**
 * What one side has seen of a battle, from its protocol lines: who is on the field, their last known condition, the
 * turn, and which of the side's creatures fought each opponent that fainted (experience goes to them).
 * <p>
 * Not thread-safe: the battle feeds it its lines in order, one packet at a time.
 */
public class BattleTracker {

    private final String side;
    private final Map<String, String> active = new HashMap<>();
    private final Map<String, Condition> conditions = new HashMap<>();
    private final Set<String> participants = new LinkedHashSet<>();
    private final List<Defeat> defeats = new ArrayList<>();
    private int turn;

    /**
     * An opponent that fainted, and the side's creatures that were on the field against it.
     */
    public record Defeat(String label, Set<String> participants) {

        public Defeat {
            participants = Set.copyOf(participants);
        }
    }

    /**
     * @param side the viewing side, {@code p1}...
     */
    public BattleTracker(String side) {
        this.side = side;
    }

    public void accept(ProtocolLine line) {
        switch (line.type()) {
            case "switch", "drag", "replace" -> this.onSwitch(line);
            case "-damage", "-heal", "-sethp" -> this.onCondition(PokemonRef.parse(line.arg(0)), line.arg(1));
            case "-status" -> this.onStatus(PokemonRef.parse(line.arg(0)), line.arg(1));
            case "-curestatus" -> this.onStatus(PokemonRef.parse(line.arg(0)), "");
            case "faint" -> this.onFaint(PokemonRef.parse(line.arg(0)));
            case "turn" -> this.turn = parseInt(line.arg(0));
            default -> {
            }
        }
    }

    private void onSwitch(ProtocolLine line) {
        PokemonRef ref = PokemonRef.parse(line.arg(0));
        if (ref == null)
            return;
        this.active.put(ref.side(), ref.label());
        this.onCondition(ref, line.arg(2));

        if (ref.side().equals(this.side)) {
            this.participants.add(ref.label());
        } else {
            // A new opponent: it fights whoever is out now
            this.participants.clear();
            String ally = this.active.get(this.side);
            if (ally != null)
                this.participants.add(ally);
        }
    }

    private void onCondition(@Nullable PokemonRef ref, String value) {
        Condition condition = Condition.parse(value);
        if (ref != null && condition != null)
            this.conditions.put(key(ref.side(), ref.label()), condition);
    }

    private void onStatus(@Nullable PokemonRef ref, String status) {
        if (ref == null)
            return;
        Condition condition = this.conditions.get(key(ref.side(), ref.label()));
        if (condition != null)
            this.conditions.put(key(ref.side(), ref.label()), new Condition(condition.hp(), condition.maxHp(), status));
    }

    private void onFaint(@Nullable PokemonRef ref) {
        if (ref == null)
            return;
        Condition condition = this.conditions.get(key(ref.side(), ref.label()));
        this.conditions.put(key(ref.side(), ref.label()),
                new Condition(0, condition == null ? 0 : condition.maxHp(), "fnt"));
        if (ref.side().equals(this.side))
            this.participants.remove(ref.label());
        else
            this.defeats.add(new Defeat(ref.label(), this.participants));
    }

    /**
     * The label of the creature a side has on the field, null before the first switch.
     */
    public @Nullable String active(String side) {
        return this.active.get(side);
    }

    public @Nullable Condition condition(String side, String label) {
        return this.conditions.get(key(side, label));
    }

    public List<Defeat> defeats() {
        return List.copyOf(this.defeats);
    }

    public int turn() {
        return this.turn;
    }

    private static String key(String side, String label) {
        return side + ":" + label;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
