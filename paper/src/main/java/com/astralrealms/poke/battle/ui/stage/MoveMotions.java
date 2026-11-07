package com.astralrealms.poke.battle.ui.stage;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * How each move moves its creature on stage, by move id: content (poke-content writes {@code motions.yml} next to the
 * move skills), so no move is named here. A move missing from the file stays where it stands.
 * <p>
 * <pre>{@code
 * tackle:   { motion: dash }
 * bodyslam: { motion: leap, height: 2.5 }
 * dig:      { motion: burrow }
 * }</pre>
 */
public final class MoveMotions {

    public static final MoveMotions NONE = new MoveMotions(Map.of());

    /**
     * What the creature does around its strike.
     */
    public enum Kind {
        /** Runs to the target, strikes, comes back. */
        DASH,
        /** Jumps onto the target in an arc ({@code height}), strikes, hops back. */
        LEAP,
        /** Steps {@code reach} blocks towards the target, strikes, steps back. */
        LUNGE,
        /** Jumps in place ({@code height}) and strikes as it lands. */
        HOP,
        /** Sinks into the ground, comes up under the target, strikes, goes back underground. */
        BURROW,
        /** Flies straight up ({@code height}), dives onto the target, strikes, glides back. */
        SOAR
    }

    /**
     * @param speed  blocks per second towards the target; 0 for the kind's own
     * @param height blocks: the top of a leap, a hop or a soar; 0 for the kind's own
     * @param reach  blocks a lunge steps forward; 0 for the kind's own
     */
    public record Motion(Kind kind, double speed, double height, double reach) {
    }

    private final Map<String, Motion> motions;

    private MoveMotions(Map<String, Motion> motions) {
        this.motions = Map.copyOf(motions);
    }

    public @Nullable Motion get(@Nullable String move) {
        return move == null ? null : this.motions.get(move);
    }

    public int size() {
        return this.motions.size();
    }

    /**
     * Reads the file; a missing file means no move moves, an unknown motion is skipped with a warning.
     */
    public static MoveMotions load(File file, Logger logger) {
        if (!file.isFile())
            return NONE;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Motion> motions = new HashMap<>();
        for (String move : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(move);
            if (section == null)
                continue;
            String kind = section.getString("motion", "");
            if (kind.equalsIgnoreCase("stay"))
                continue;
            try {
                motions.put(move, new Motion(Kind.valueOf(kind.toUpperCase(Locale.ROOT)), section.getDouble("speed"),
                        section.getDouble("height"), section.getDouble("reach")));
            } catch (IllegalArgumentException e) {
                logger.warn("Unknown motion '{}' for move {} in {}, ignored", kind, move, file.getName());
            }
        }
        return new MoveMotions(motions);
    }
}
