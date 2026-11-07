package com.astralrealms.poke.battle.ui.stage;

import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import com.astralrealms.core.placeholder.container.PlaceholderContainer;
import com.astralrealms.poke.battle.PokeBattle;
import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;
import com.astralrealms.poke.core.PokeCoreAPI;
import com.astralrealms.poke.core.species.MoveData;
import com.astralrealms.skill.SkillAPI;
import com.astralrealms.skill.skill.SkillService;

/**
 * Moves as AstralSkill skills cast by the attacking creature (a skill needs a real entity as caster), from
 * {@code creatures.moves.height} above its feet towards the same height above the target's. The skill is the first of
 * {@code creatures.moves.skills} that exists; it reads the distance to the target as {@code %move-distance%}, so a
 * projectile can end, and chain its impact, right on the target. Only created when AstralSkill is enabled.
 */
public class AstralSkillEffects implements MoveEffects {

    private final PokeBattle plugin;

    public AstralSkillEffects(PokeBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean play(Entity attacker, Entity target, String move) {
        PokeBattleConfiguration.MoveEffects config = this.config();
        if (config == null)
            return false;
        SkillService skills = SkillAPI.service();
        String skill = this.skill(skills, config, move);
        return skill != null && this.cast(skills, config, attacker, target, skill);
    }

    @Override
    public boolean stat(Entity creature, Entity facing, boolean up) {
        PokeBattleConfiguration.MoveEffects config = this.config();
        String skill = config == null ? null : up ? config.statUp() : config.statDown();
        if (skill == null || skill.isBlank())
            return false;
        SkillService skills = SkillAPI.service();
        return skills.findById(skill).isPresent() && this.cast(skills, config, creature, facing, skill);
    }

    private PokeBattleConfiguration.@Nullable MoveEffects config() {
        PokeBattleConfiguration.Creatures creatures = this.plugin.configuration().creatures();
        PokeBattleConfiguration.MoveEffects config = creatures == null ? null : creatures.moves();
        return config == null || !config.enabled() ? null : config;
    }

    /**
     * Casts {@code skill} from {@code caster} towards {@code target}.
     */
    private boolean cast(SkillService skills, PokeBattleConfiguration.MoveEffects config, Entity caster, Entity target,
                         String skill) {
        if (!caster.getWorld().equals(target.getWorld()))
            return false;
        Location from = caster.getLocation().add(0, config.height(), 0);
        Location to = target.getLocation().add(0, config.height(), 0);
        Vector towards = to.toVector().subtract(from.toVector());
        double distance = towards.length();
        // Towards the target; the way the caster faces if both stand at the same place
        Vector3d direction = (distance < 1.0E-6 ? caster.getLocation().getDirection() : towards.normalize())
                .toVector3d();
        PlaceholderContainer container = new PlaceholderContainer()
                .registerDirect("move-distance", (float) distance);
        try {
            // castAtPos, not tryCastAtPos: the servers' AstralSkill builds do not all have the latter
            skills.castAtPos(caster, caster.getUniqueId(), skill, from, direction, container);
            return true;
        } catch (RuntimeException | LinkageError e) {
            // An effect never stops the battle from going on
            this.plugin.getSLF4JLogger().warn("Could not play skill {}: {}", skill, e.toString());
            return false;
        }
    }

    /**
     * The first configured skill that exists for this move; null when none does.
     */
    private @Nullable String skill(SkillService skills, PokeBattleConfiguration.MoveEffects config, String move) {
        MoveData data = PokeCoreAPI.get().species().findMove(move).orElse(null);
        String type = data == null ? "" : data.type().toLowerCase(Locale.ROOT);
        String category = data == null ? "" : data.category().name().toLowerCase(Locale.ROOT);
        for (String pattern : config.skills()) {
            if ((pattern.contains("{type}") || pattern.contains("{category}")) && data == null)
                continue;
            String id = pattern.replace("{move}", move).replace("{type}", type).replace("{category}", category);
            if (skills.findById(id).isPresent())
                return id;
        }
        return null;
    }
}
