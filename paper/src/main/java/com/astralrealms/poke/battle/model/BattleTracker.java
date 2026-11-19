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
 * What one side has seen of a battle, from its protocol lines: who is on the field at each position ({@code p1a},
 * {@code p1b} in doubles), their last known condition, the turn, and which of the side's creatures fought each opponent
 * that fainted (experience goes to them).
 * <p>
 * Not thread-safe: the battle feeds it its lines in order, one packet at a time.
 */
public class BattleTracker {

    private final String side;
    /** By position ({@code p1a}, {@code p2b}...), the label of the creature standing there. */
    private final Map<String, String> active = new HashMap<>();
    private final Map<String, Condition> conditions = new HashMap<>();
    /** By opponent on the field, the side's creatures that were out against it. */
    private final Map<String, Set<String>> participants = new HashMap<>();
    private final List<Defeat> defeats = new ArrayList<>();
    private @Nullable Capture capture;
    private int turn;
    /** Creatures each side has on the field at once: 1 in singles, 2 in doubles. */
    private int slots = 1;

    /**
     * An opponent that fainted, and the side's creatures that were on the field against it.
     */
    public record Defeat(String label, Set<String> participants) {

        public Defeat {
            participants = Set.copyOf(participants);
        }
    }

    /**
     * An opponent caught with {@code ball}, and the side's creatures that were on the field against it.
     */
    public record Capture(String label, String ball, Set<String> participants) {

        public Capture {
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
            case "gametype" -> this.slots = Math.max(this.slots, slots(line.arg(0)));
            case "polaris-ball" -> this.onBall(line);
            default -> {
            }
        }
    }

    private void onSwitch(ProtocolLine line) {
        PokemonRef ref = PokemonRef.parse(line.arg(0));
        if (ref == null || ref.slot() < 0)
            return;
        this.slots = Math.max(this.slots, ref.slot() + 1);
        String replaced = this.active.put(ref.key(), ref.label());
        this.onCondition(ref, line.arg(2));

        if (ref.side().equals(this.side)) {
            // It fights every opponent out now
            this.participants.values().forEach(fought -> fought.add(ref.label()));
            return;
        }
        // A new opponent: it fights whoever is out now
        if (replaced != null && !replaced.equals(ref.label()))
            this.participants.remove(replaced);
        Set<String> fought = new LinkedHashSet<>();
        for (int slot = 0; slot < this.slots; slot++) {
            String ally = this.active.get(this.side + PokemonRef.position(slot));
            if (ally != null)
                fought.add(ally);
        }
        this.participants.put(ref.label(), fought);
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
            this.participants.values().forEach(fought -> fought.remove(ref.label()));
        else
            this.defeats.add(new Defeat(ref.label(), this.participants.getOrDefault(ref.label(), Set.of())));
    }

    /**
     * {@code |polaris-ball|<target>|<ball>|<shakes>|caught|free}
     */
    private void onBall(ProtocolLine line) {
        PokemonRef target = PokemonRef.parse(line.arg(0));
        if (target != null && !target.side().equals(this.side) && line.arg(3).equals("caught"))
            this.capture = new Capture(target.label(), line.arg(1),
                    this.participants.getOrDefault(target.label(), Set.of()));
    }

    /**
     * The opponent this side caught, if it did (the battle ends with it).
     */
    public @Nullable Capture capture() {
        return this.capture;
    }

    /**
     * The label of the creature a side has on the field (its first position in doubles), null before the first
     * switch.
     */
    public @Nullable String active(String side) {
        return this.active(side, 0);
    }

    /**
     * The label of the creature at a side's position ({@code 0} for {@code a}, {@code 1} for {@code b}), null while
     * nobody was sent there. A fainted creature stays there until another takes its place.
     */
    public @Nullable String active(String side, int slot) {
        return this.active.get(side + PokemonRef.position(slot));
    }

    /**
     * Creatures each side has on the field at once: 1 in singles, 2 in doubles ({@code |gametype|}, or the positions
     * seen).
     */
    public int slots() {
        return this.slots;
    }

    public boolean doubles() {
        return this.slots > 1;
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

    private static int slots(String gameType) {
        return switch (gameType) {
            case "doubles" -> 2;
            case "triples" -> 3;
            default -> 1;
        };
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
