package com.astralrealms.poke.battle.ui.stage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.battle.PokeBattle;
import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;
import com.astralrealms.poke.battle.ui.BattleCue;
import com.astralrealms.poke.battle.ui.BattleStage;
import com.astralrealms.poke.battle.ui.BattleUi;
import com.astralrealms.poke.battle.ui.ChoiceView;
import com.astralrealms.poke.battle.ui.FieldView;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;

/**
 * The battle's creatures standing on its stage, around whichever UI the player has (HUD or dialog): each side's
 * active creature, seen only by the player, playing the moments of the battle as the updates come.
 * <p>
 * Also owns the battle's stage: it is dropped when the battle closes. Without {@link CreatureActors} (PokeEntities
 * missing) or with {@code creatures.enabled: false}, it only passes calls on.
 * <p>
 * Main thread only (the battle calls the UI on the player's thread, and PokeBattle does not run on Folia).
 */
public class StagedUi implements BattleUi {

    private final PokeBattle plugin;
    private final BattleUi ui;
    private final @Nullable CreatureActors actors;
    private final Map<UUID, Scene> scenes = new HashMap<>();

    public StagedUi(PokeBattle plugin, BattleUi ui, @Nullable CreatureActors actors) {
        this.plugin = plugin;
        this.ui = ui;
        this.actors = actors;
    }

    @Override
    public void open(Player player, FieldView field) {
        this.ui.open(player, field);
        Scene scene = this.scene(player, field.battleId());
        if (scene == null)
            return;
        // A creature already out (coming back with /combat, a battle shown after a reload) stands there at once
        scene.sync(Side.ALLY, field.ally());
        scene.sync(Side.FOE, field.foe());
    }

    @Override
    public void update(Player player, FieldView field, List<Component> log, List<BattleCue> cues) {
        this.ui.update(player, field, log, cues);
        Scene scene = this.scene(player, field.battleId());
        PokeBattleConfiguration.Creatures config = this.plugin.configuration().creatures();
        if (scene == null || config == null)
            return;
        long spacing = ticks(config.cueSpacing());
        long delay = 0;
        for (BattleCue cue : cues) {
            Runnable moment = switch (cue) {
                case ALLY_SWITCH -> () -> scene.enter(Side.ALLY, field.ally());
                case FOE_SWITCH -> () -> scene.enter(Side.FOE, field.foe());
                case ALLY_MOVE -> () -> scene.animate(Side.ALLY, "move");
                case FOE_MOVE -> () -> scene.animate(Side.FOE, "move");
                case ALLY_DAMAGE -> () -> scene.animate(Side.ALLY, "damage");
                case FOE_DAMAGE -> () -> scene.animate(Side.FOE, "damage");
                case ALLY_FAINT -> () -> scene.faint(Side.ALLY);
                case FOE_FAINT -> () -> scene.faint(Side.FOE);
                case CAPTURE -> () -> scene.leave(Side.FOE, 0);
                case END -> null;
            };
            if (moment == null)
                continue;
            scene.later(delay, moment);
            delay += spacing;
        }
    }

    @Override
    public void choose(Player player, ChoiceView view) {
        this.ui.choose(player, view);
    }

    @Override
    public void waiting(Player player) {
        this.ui.waiting(player);
    }

    @Override
    public void close(Player player, UUID battleId) {
        this.ui.close(player, battleId);
        this.plugin.stages().remove(battleId);
        Scene scene = this.scenes.remove(battleId);
        PokeBattleConfiguration.Creatures config = this.plugin.configuration().creatures();
        if (scene != null)
            scene.end(config == null ? 0 : ticks(config.closeDelay()));
    }

    @Override
    public void shutdown() {
        this.ui.shutdown();
        this.scenes.values().forEach(scene -> scene.end(0));
        this.scenes.clear();
        this.plugin.stages().clear();
    }

    /**
     * The battle's scene, set on its stage the first time; null when no creature is shown.
     */
    private @Nullable Scene scene(Player player, UUID battleId) {
        PokeBattleConfiguration configuration = this.plugin.configuration();
        if (this.actors == null || configuration.creatures() == null || !configuration.creatures().enabled())
            return null;
        return this.scenes.computeIfAbsent(battleId, id ->
                new Scene(player, this.plugin.stages().stage(player, id, configuration), this.actors));
    }

    private static long ticks(@Nullable Duration duration) {
        return duration == null ? 0 : duration.toMillis() / 50;
    }

    private enum Side {
        ALLY,
        FOE
    }

    private record Actor(String species, Entity entity) {
    }

    /**
     * One battle's creatures and the moments still to play.
     */
    private final class Scene {

        private final Player player;
        private final BattleStage stage;
        private final CreatureActors actors;
        private final Map<Side, Actor> standing = new HashMap<>();
        private final List<ScheduledTask> pending = new ArrayList<>();
        private boolean ended;

        Scene(Player player, BattleStage stage, CreatureActors actors) {
            this.player = player;
            this.stage = stage;
            this.actors = actors;
        }

        /**
         * The side's creature now: put there if it is not already (an empty species or a fainted one is left out).
         */
        void sync(Side side, ChoiceView.Creature creature) {
            if (creature.species().isEmpty() || (creature.maxHp() > 0 && creature.hp() <= 0))
                return;
            Actor actor = this.standing.get(side);
            if (actor != null && actor.species().equals(creature.species()) && actor.entity().isValid())
                return;
            this.leave(side, 0);
            Location spot = side == Side.ALLY ? this.stage.ally() : this.stage.foe();
            Entity entity = this.actors.spawn(this.player, creature.species(), spot);
            if (entity != null)
                this.standing.put(side, new Actor(creature.species(), entity));
        }

        /**
         * A creature was sent out: it takes the spot and plays its entrance.
         */
        void enter(Side side, ChoiceView.Creature creature) {
            this.leave(side, 0);
            this.sync(side, creature);
            this.animate(side, "switch");
        }

        /**
         * Plays the first of the moment's animations ({@code creatures.animations}) the model has.
         */
        void animate(Side side, String moment) {
            Actor actor = this.standing.get(side);
            PokeBattleConfiguration.Creatures config = plugin.configuration().creatures();
            if (actor == null || config == null || !actor.entity().isValid())
                return;
            for (String animation : config.animations(moment))
                if (this.actors.animate(actor.entity(), animation))
                    return;
        }

        void faint(Side side) {
            this.animate(side, "faint");
            PokeBattleConfiguration.Creatures config = plugin.configuration().creatures();
            this.leave(side, config == null ? 0 : ticks(config.faintLinger()));
        }

        /**
         * The side's creature goes, after {@code delay} ticks; the spot is free at once.
         */
        void leave(Side side, long delay) {
            Actor actor = this.standing.remove(side);
            if (actor == null)
                return;
            Entity entity = actor.entity();
            if (delay <= 0) {
                this.actors.remove(entity);
                return;
            }
            // On the creature's own scheduler: it runs even if the player has left
            entity.getScheduler().runDelayed(plugin, _ -> this.actors.remove(entity), () -> this.actors.remove(entity),
                    delay);
        }

        /**
         * Runs a moment {@code delay} ticks from now, unless the battle has ended by then.
         */
        void later(long delay, Runnable moment) {
            if (delay <= 0) {
                moment.run();
                return;
            }
            ScheduledTask task = this.player.getScheduler().runDelayed(plugin, _ -> {
                if (!this.ended)
                    moment.run();
            }, null, delay);
            if (task != null)
                this.pending.add(task);
        }

        /**
         * The battle is over: no more moments, and the creatures go after {@code delay} ticks.
         */
        void end(long delay) {
            this.ended = true;
            this.pending.forEach(ScheduledTask::cancel);
            this.pending.clear();
            for (Side side : Side.values())
                this.leave(side, delay);
        }
    }
}
