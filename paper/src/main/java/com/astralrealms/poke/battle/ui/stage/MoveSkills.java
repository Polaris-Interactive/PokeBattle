package com.astralrealms.poke.battle.ui.stage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

/**
 * Which skill plays each move and the placeholders its skills read, by move id: content (poke-content writes
 * {@code move-skills.yml} with the move skills), so no move is named here. It lets moves share skills: a shared skill
 * reads what differs between moves from placeholders, e.g. {@code cast-skill-on-miss: ["%move-reach-next%"]}.
 * <p>
 * <pre>{@code
 * slam:       { skill: move-fx-reach, placeholders: { reach-next: move-slam-smack } }
 * furyattack: { placeholders: { wait-next: move-furyattack-jab-2, wait-range: 0.006 } }
 * }</pre>
 * {@code skill} is tried before {@code creatures.moves.skills}; each placeholder is set as {@code %move-<key>%} (numbers
 * as floats, AstralSkill's number type), along with {@code %move-id%}, for every skill the move casts. A move missing
 * from the file plays {@code creatures.moves.skills} as before.
 */
public final class MoveSkills {

    public static final MoveSkills NONE = new MoveSkills(Map.of());

    /**
     * @param skill        the skill to cast for the move, before the configured ones; null for those only
     * @param placeholders what its skills read, by key without the {@code move-} prefix
     */
    public record Entry(@Nullable String skill, Map<String, Object> placeholders) {
    }

    private final Map<String, Entry> entries;

    private MoveSkills(Map<String, Entry> entries) {
        this.entries = Map.copyOf(entries);
    }

    public @Nullable Entry get(@Nullable String move) {
        return move == null ? null : this.entries.get(move);
    }

    public int size() {
        return this.entries.size();
    }

    /**
     * The skill ids to try for {@code move}, first found plays: its mapped skill, then {@code patterns} with
     * {@code {move}}, {@code {type}} and {@code {category}} filled in. A pattern naming the type or category is skipped
     * when those are unknown (null).
     */
    public List<String> candidates(String move, List<String> patterns, @Nullable String type,
                                   @Nullable String category) {
        List<String> candidates = new ArrayList<>();
        Entry entry = this.entries.get(move);
        if (entry != null && entry.skill() != null)
            candidates.add(entry.skill());
        for (String pattern : patterns) {
            if ((pattern.contains("{type}") || pattern.contains("{category}")) && (type == null || category == null))
                continue;
            candidates.add(pattern.replace("{move}", move)
                    .replace("{type}", type == null ? "" : type)
                    .replace("{category}", category == null ? "" : category));
        }
        return candidates;
    }

    /**
     * The placeholders the skills of {@code move} read, keyed by their full name: {@code move-id} and the mapped ones.
     */
    public Map<String, Object> placeholders(String move) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("move-id", move);
        Entry entry = this.entries.get(move);
        if (entry != null)
            entry.placeholders().forEach((key, value) -> placeholders.put("move-" + key, value));
        return placeholders;
    }

    /**
     * Reads the file; a missing file means every move plays its {@code move-<id>} skill as before.
     */
    public static MoveSkills load(File file) {
        if (!file.isFile())
            return NONE;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Entry> entries = new HashMap<>();
        for (String move : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(move);
            if (section == null)
                continue;
            String skill = section.getString("skill");
            Map<String, Object> placeholders = new HashMap<>();
            ConfigurationSection values = section.getConfigurationSection("placeholders");
            if (values != null) {
                for (String key : values.getKeys(false)) {
                    Object value = values.get(key);
                    if (value instanceof Number number)
                        placeholders.put(key, number.floatValue());
                    else if (value != null)
                        placeholders.put(key, String.valueOf(value));
                }
            }
            entries.put(move, new Entry(skill == null || skill.isBlank() ? null : skill, Map.copyOf(placeholders)));
        }
        return new MoveSkills(entries);
    }
}
