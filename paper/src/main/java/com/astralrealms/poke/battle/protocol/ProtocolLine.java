package com.astralrealms.poke.battle.protocol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * One line of the simulator protocol: {@code |type|arg|arg|[kwarg] value}. Keyword arguments ({@code [from]},
 * {@code [of]}, {@code [silent]}...) are the trailing arguments in brackets.
 */
public record ProtocolLine(String type, List<String> args, Map<String, String> tags) {

    public ProtocolLine {
        args = List.copyOf(args);
        tags = Map.copyOf(tags);
    }

    /**
     * @return null for a line that is not a protocol message (plain text, empty)
     */
    public static @Nullable ProtocolLine parse(String line) {
        if (line.length() < 2 || line.charAt(0) != '|')
            return null;
        String[] parts = line.substring(1).split("\\|", -1);
        List<String> args = new ArrayList<>();
        Map<String, String> tags = new HashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            int close = part.indexOf(']');
            if (part.startsWith("[") && close > 0) {
                tags.put(part.substring(1, close), part.substring(close + 1).trim());
                continue;
            }
            args.add(part);
        }
        return new ProtocolLine(parts[0], args, tags);
    }

    public String arg(int index) {
        return index < this.args.size() ? this.args.get(index) : "";
    }

    public boolean has(String tag) {
        return this.tags.containsKey(tag);
    }

    public @Nullable String tag(String tag) {
        return this.tags.get(tag);
    }

    /**
     * The simulator's id of a name: lowercase letters and digits only ({@code "Thunder Punch"} is
     * {@code thunderpunch}), which is also the id the species bundle uses.
     */
    public static String toId(@Nullable String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    /**
     * The effect of a {@code [from]} tag without its kind: {@code "item: Charcoal"} is {@code charcoal}, and its kind
     * {@code item}.
     */
    public static Effect effect(@Nullable String value) {
        if (value == null || value.isEmpty())
            return new Effect("", "");
        int colon = value.indexOf(':');
        if (colon < 0)
            return new Effect("", toId(value));
        return new Effect(toId(value.substring(0, colon)), toId(value.substring(colon + 1)));
    }

    /**
     * @param kind {@code item}, {@code ability}, {@code move}... or empty for a status or a condition
     */
    public record Effect(String kind, String id) {
    }
}
