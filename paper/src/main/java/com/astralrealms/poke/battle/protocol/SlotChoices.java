package com.astralrealms.poke.battle.protocol;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.experimental.UtilityClass;

/**
 * A doubles answer: one choice per active slot, joined by {@code ", "} in slot order, as Showdown reads it
 * ({@code move 1 2, switch 3}). Each slot moves (with a target when its move needs one), switches, or passes when it
 * has nothing to do; two slots never send in the same bench member.
 */
@UtilityClass
public class SlotChoices {

    public static final String PASS = "pass";
    private static final String SEPARATOR = ", ";

    /**
     * The answer from each slot's choice, in slot order.
     */
    public static String join(List<String> parts) {
        return String.join(SEPARATOR, parts);
    }

    /**
     * The choice of each slot in an answer.
     */
    public static List<String> split(String choice) {
        List<String> parts = new ArrayList<>();
        for (String part : choice.split(","))
            parts.add(part.trim());
        return parts;
    }

    /**
     * The bench member a slot's choice sends in, -1 when it does not switch.
     */
    public static int switched(String part) {
        String[] words = part.trim().split(" ");
        if (words.length != 2 || !words[0].equals("switch"))
            return -1;
        try {
            return Integer.parseInt(words[1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Whether a doubles answer is legal for the request: one choice per slot, each legal for its slot, no bench member
     * sent in twice. Balls, bag items and fleeing take a whole side's turn on the worker: never in doubles.
     * <p>
     * A slot passes when it has nothing to do; a forced one only when there are fewer bench members than forced slots
     * (as Showdown counts its forced passes), whichever of them passes.
     */
    public static boolean legal(ChoiceRequest request, String choice) {
        List<String> parts = split(choice);
        if (request.waiting() || parts.size() != request.slots().size())
            return false;
        long forced = request.slots().stream().filter(ChoiceRequest.Slot::forced).count();
        long bench = request.team().stream().filter(member -> request.onBench(member.position())).count();
        long passes = Math.max(0, forced - bench);
        Set<Integer> taken = new HashSet<>();
        for (ChoiceRequest.Slot slot : request.slots()) {
            String part = parts.get(slot.index());
            if (part.equals(PASS)) {
                if (!slot.idle() && (!slot.forced() || passes-- <= 0))
                    return false;
                continue;
            }
            if (!legal(request, slot, part, taken))
                return false;
        }
        return true;
    }

    private static boolean legal(ChoiceRequest request, ChoiceRequest.Slot slot, String part, Set<Integer> taken) {
        String[] words = part.split(" ");
        int index = words.length < 2 ? -1 : number(words[1]);
        if (index < 0)
            return false;
        return switch (words[0]) {
            case "move" -> {
                if (request.forceSwitch() || words.length > 3 || !slot.canUseMove(index))
                    yield false;
                ChoiceRequest.MoveOption move = slot.move(index).orElse(null);
                List<Integer> targets = move == null
                        ? List.of()
                        : ChoiceRequest.targets(move.target(), slot.index(), request.slots().size());
                if (targets.isEmpty())
                    yield words.length == 2;
                yield words.length == 3 && targets.contains(signed(words[2]));
            }
            case "switch" -> {
                if (words.length != 2 || slot.idle() || (request.forceSwitch() && !slot.forced())
                        || (slot.trapped() && !request.forceSwitch()) || !request.onBench(index))
                    yield false;
                yield taken.add(index);
            }
            default -> false;
        };
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int signed(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
