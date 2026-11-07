package com.astralrealms.poke.battle.ui.stage;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import com.astralrealms.core.placeholder.container.PlaceholderContainer;
import com.astralrealms.poke.battle.PokeBattle;
import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;
import com.astralrealms.poke.battle.configuration.PokeBattleMessages;

import net.kyori.adventure.text.Component;

/**
 * What a hit shows on the creature that takes it: its model flashes red, and the HP it lost rises above it as a text
 * display only the player sees. Main thread only.
 */
public class HitFeedback {

    /** Blocks above the creature's hitbox a number starts at. */
    private static final double NUMBER_GAP = 0.3;
    /** Blocks a number may start off the creature's centre, sideways, so two hits in a row do not overlap. */
    private static final double NUMBER_SPREAD = 0.35;

    private final PokeBattle plugin;
    private final CreatureActors actors;

    public HitFeedback(PokeBattle plugin, CreatureActors actors) {
        this.plugin = plugin;
        this.actors = actors;
    }

    /**
     * @param creature the creature hit, standing on {@code viewer}'s stage
     * @param ally     whether it is the player's creature (its HP in points; the opponent's is in percent)
     * @param lost     HP lost; nothing rises for 0 or less
     */
    public void hit(Player viewer, Entity creature, boolean ally, int lost) {
        PokeBattleConfiguration.Creatures config = this.plugin.configuration().creatures();
        if (config == null || !creature.isValid())
            return;
        PokeBattleConfiguration.Hit hit = config.hitOrDefault();
        if (hit.flash())
            this.actors.flash(creature);
        if (hit.numbers() && lost > 0)
            this.number(viewer, creature, (ally ? PokeBattleMessages.HIT_DAMAGE_ALLY : PokeBattleMessages.HIT_DAMAGE_FOE)
                    .component(new PlaceholderContainer().registerDirect("amount", lost)), hit);
    }

    private void number(Player viewer, Entity creature, Component text, PokeBattleConfiguration.Hit hit) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location at = creature.getLocation()
                .add(random.nextDouble(-NUMBER_SPREAD, NUMBER_SPREAD), creature.getHeight() + NUMBER_GAP,
                        random.nextDouble(-NUMBER_SPREAD, NUMBER_SPREAD));
        float scale = hit.numberScale().floatValue();
        TextDisplay display = creature.getWorld().spawn(at, TextDisplay.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setVisibleByDefault(false);
            spawned.text(text);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setShadowed(true);
            spawned.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            spawned.setTransformation(transformation(0, scale));
        });
        viewer.showEntity(this.plugin, display);

        long life = Math.max(1, hit.numberLife().toMillis() / 50);
        float rise = hit.numberRise().floatValue();
        // Next tick, once the client has it: it glides up for its whole life
        display.getScheduler().runDelayed(this.plugin, _ -> {
            display.setInterpolationDelay(0);
            display.setInterpolationDuration((int) life);
            display.setTransformation(transformation(rise, scale));
        }, null, 1);
        display.getScheduler().runDelayed(this.plugin, _ -> display.remove(), null, life + 1);
    }

    private static Transformation transformation(float up, float scale) {
        return new Transformation(new Vector3f(0, up, 0), new AxisAngle4f(), new Vector3f(scale), new AxisAngle4f());
    }
}
