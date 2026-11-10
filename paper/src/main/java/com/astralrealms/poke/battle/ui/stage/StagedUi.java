package com.astralrealms.poke.battle.ui.stage;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.battle.PokeBattle;
import com.astralrealms.poke.battle.api.model.BattleOutcome;
import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;
import com.astralrealms.poke.battle.ui.BattleBeat;
import com.astralrealms.poke.battle.ui.BattleCue;
import com.astralrealms.poke.battle.ui.BattleStage;
import com.astralrealms.poke.battle.ui.BattleUi;
import com.astralrealms.poke.battle.ui.ChoiceView;
import com.astralrealms.poke.battle.ui.FieldView;
import com.astralrealms.poke.core.PokeCoreAPI;
import com.astralrealms.poke.core.species.MoveData;


/**
 * The battle's creatures standing on its stage, around whichever UI the player has (HUD or dialog): each side's
 * active creature, seen only by the player, playing each beat of the battle as {@link com.astralrealms.poke.battle.ui.PacedUi}
 * plays it.
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
    private final @Nullable MoveEffects effects;
    private final @Nullable HitFeedback hits;
    private final Map<UUID, Scene> scenes = new HashMap<>();
    /** By player, the ticks a move that struck early left unspent: the next beat of the run takes them. */
    private final Map<UUID, Long> owed = new HashMap<>();

    /**
     * @param effects what moves look like between the creatures; null for none (AstralSkill missing)
     */
    public StagedUi(PokeBattle plugin, BattleUi ui, @Nullable CreatureActors actors, @Nullable MoveEffects effects) {
        this.plugin = plugin;
        this.ui = ui;
        this.actors = actors;
        this.effects = effects;
        this.hits = actors == null ? null : new HitFeedback(plugin, actors);
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
        scene.seen(field);
    }

    @Override
    public Pace pace(Player player, BattleBeat beat) {
        Pace pace = this.ui.pace(player, beat);
        PokeBattleConfiguration.Creatures config = this.plugin.configuration().creatures();
        if (beat.cue() == null || config == null || this.scene(player, beat.field().battleId()) == null)
            return pace;
        // A moment the creatures play gets its time even where nothing else is shown (the dialog, a cue without a shot);
        // a move longer, so its effect lands before the hit shows
        if (beat.cue() == BattleCue.END)
            return pace;
        boolean move = beat.cue() == BattleCue.ALLY_MOVE || beat.cue() == BattleCue.FOE_MOVE;
        Duration spacing = move && config.moveSpacing() != null ? config.moveSpacing() : config.cueSpacing();
        // What a move that struck early left unspent goes to the beat after it: the turn keeps its rhythm
        Long owed = this.owed.remove(player.getUniqueId());
        Pace full = pace.with(new Pace(0, ticks(spacing) + (owed == null ? 0 : owed)));
        // A creature that goes to its target strikes it there: the next beat (the hit: its flash, the HP lost) comes
        // with the strike, not once the creature is back on its spot
        long impact = !move ? -1 : this.scene(player, beat.field().battleId())
                .impact(beat.cue() == BattleCue.ALLY_MOVE ? Side.ALLY : Side.FOE, beat.move());
        if (impact < 0)
            return full;
        long hold = Math.max(1, impact);
        if (full.hold() > hold)
            this.owed.put(player.getUniqueId(), full.hold() - hold);
        return new Pace(full.lead(), hold);
    }

    @Override
    public void cue(Player player, BattleBeat beat) {
        this.ui.cue(player, beat);
    }

    @Override
    public void update(Player player, BattleBeat beat) {
        // The creatures first: the camera frames its shot for who stands there once the beat shows
        this.stage(player, beat);
        this.ui.update(player, beat);
    }

    private void stage(Player player, BattleBeat beat) {
        Scene scene = this.scene(player, beat.field().battleId());
        if (scene == null || beat.cue() == null)
            return;
        FieldView field = beat.field();
        switch (beat.cue()) {
            case ALLY_SWITCH -> scene.enter(Side.ALLY, field.ally());
            case FOE_SWITCH -> scene.enter(Side.FOE, field.foe());
            case ALLY_MOVE -> scene.attack(Side.ALLY, beat.move());
            case FOE_MOVE -> scene.attack(Side.FOE, beat.move());
            case ALLY_DAMAGE -> scene.hit(Side.ALLY, field.ally());
            case FOE_DAMAGE -> scene.hit(Side.FOE, field.foe());
            case ALLY_BOOST -> scene.stat(Side.ALLY, true);
            case FOE_BOOST -> scene.stat(Side.FOE, true);
            case ALLY_UNBOOST -> scene.stat(Side.ALLY, false);
            case FOE_UNBOOST -> scene.stat(Side.FOE, false);
            case ALLY_FAINT -> scene.faint(Side.ALLY);
            case FOE_FAINT -> scene.faint(Side.FOE);
            case CAPTURE -> scene.leave(Side.FOE, 0);
            case END -> {
            }
        }
        scene.seen(field);
    }

    @Override
    public void idle(Player player) {
        this.owed.remove(player.getUniqueId());
        this.ui.idle(player);
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
    public void outcome(Player player, UUID battleId, BattleOutcome outcome) {
        this.ui.outcome(player, battleId, outcome);
    }

    @Override
    public void then(Player player, Runnable action) {
        this.ui.then(player, action);
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

    /** Ticks a creature stays where it struck after its effect leaves, before going back. */
    private static final long STRIKE_HOLD = 8;
    /** Blocks kept between two creatures' bodies when one strikes the other, and the least distance between them. */
    private static final double CONTACT_ROOM = 0.3;
    private static final double MIN_GAP = 1.2;

    private static long ticks(@Nullable Duration duration) {
        return duration == null ? 0 : duration.toMillis() / 50;
    }

    private enum Side {
        ALLY,
        FOE;

        /** The side as the stage and its shots name it. */
        String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * @param home its spot on the stage, where it goes back after a move that moved it
     */
    private record Actor(String species, Entity entity, Location home) {
    }

    /**
     * One battle's creatures.
     */
    private final class Scene {

        private final Player player;
        private final BattleStage stage;
        private final CreatureActors actors;
        private final Map<Side, Actor> standing = new HashMap<>();
        /** A creature moving for its move, by side. */
        private final Map<Side, StageMotion> moving = new HashMap<>();
        /** Each side's HP as last shown, to tell how much a hit took. */
        private final Map<Side, Integer> hp = new HashMap<>();

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
            if (entity == null)
                return;
            this.standing.put(side, new Actor(creature.species(), entity, spot.clone()));
            // The camera frames it, and keeps clear of it, by its size
            this.stage.body(side.key(), this.actors.size(entity));
            // Its battle stance, looping under everything else it plays (a once animation goes back to it)
            this.animate(side, "stance");
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
         * Plays the first of the moment's animations ({@code creatures.animations}) the model has, falling back to
         * {@code fallback}'s when the moment lists none.
         *
         * @return the animation played, null for none
         */
        @Nullable String animate(Side side, String moment, String fallback, @Nullable String move) {
            PokeBattleConfiguration.Creatures config = plugin.configuration().creatures();
            if (config == null)
                return null;
            return this.animate(side, config.animations(moment).isEmpty() ? fallback : moment, move);
        }

        /**
         * Plays the first of the moment's animations ({@code creatures.animations}) the model has.
         *
         * @return the animation played, null for none
         */
        @Nullable String animate(Side side, String moment) {
            return this.animate(side, moment, null);
        }

        /**
         * As {@link #animate(Side, String)}; {@code {move}} in an animation name stands for the move's id, so a model
         * can have an animation of its own for a move ({@code vinewhip}). Names left with a placeholder are skipped.
         */
        @Nullable String animate(Side side, String moment, @Nullable String move) {
            Actor actor = this.standing.get(side);
            PokeBattleConfiguration.Creatures config = plugin.configuration().creatures();
            if (actor == null || config == null || !actor.entity().isValid())
                return null;
            for (String candidate : config.animations(moment)) {
                String animation = move == null ? candidate : candidate.replace("{move}", move);
                if (animation.contains("{"))
                    continue;
                if (this.actors.animate(actor.entity(), animation))
                    return animation;
            }
            return null;
        }

        /**
         * The side's creature uses a move. A move with a motion ({@link MoveMotions}) first takes it to the other
         * creature (running, leaping, underground...), strikes there and brings it back; any other strikes from its
         * spot.
         */
        void attack(Side side, @Nullable String move) {
            Actor attacker = this.standing.get(side);
            MoveMotions.Motion motion = plugin.motions().get(move);
            Location contact = motion == null ? null : this.contact(side);
            StageMotion previous = this.moving.remove(side);
            if (previous != null)
                previous.cancel();
            if (motion == null || contact == null || !shown(attacker)) {
                this.strike(side, move);
                return;
            }
            StageMotion running = new StageMotion(plugin, attacker.entity(), attacker.home(), contact, motion,
                    this.height(side), () -> this.strike(side, move) + STRIKE_HOLD);
            this.moving.put(side, running);
            running.start();
        }

        /**
         * Ticks before the side's creature hits its target with this move, for a move that takes it there: the way,
         * then its strike up to the release marker. -1 for a move it uses from its spot (its effect travels).
         */
        long impact(Side side, @Nullable String move) {
            MoveMotions.Motion motion = plugin.motions().get(move);
            Actor attacker = this.standing.get(side);
            Location contact = motion == null ? null : this.contact(side);
            if (contact == null || !shown(attacker))
                return -1;
            return StageMotion.ticksToStrike(motion, attacker.home(), contact, this.height(side))
                    + this.release(attacker, move);
        }

        /**
         * Ticks from the start of the move's animation to its release marker, before it plays: the first animation of
         * the move's list that has the marker (the installer marks every attack animation). 0 without one.
         */
        private long release(Actor attacker, @Nullable String move) {
            PokeBattleConfiguration.Creatures config = plugin.configuration().creatures();
            String marker = config == null || config.moves() == null ? null : config.moves().releaseMarker();
            if (move == null || marker == null || marker.isBlank())
                return 0;
            MoveData data = PokeCoreAPI.get().species().findMove(move).orElse(null);
            String moment = data == null ? "move" : "move-" + data.category().name().toLowerCase(Locale.ROOT);
            for (String candidate : config.animations(config.animations(moment).isEmpty() ? "move" : moment)) {
                Duration release = this.actors.marker(attacker.entity(), candidate.replace("{move}", move), marker)
                        .orElse(null);
                if (release != null)
                    return ticks(release);
            }
            return 0;
        }

        /**
         * Where the side's creature strikes the other one from: in front of it, the two bodies apart. Null when there
         * is nobody to go to.
         */
        private @Nullable Location contact(Side side) {
            Actor self = this.standing.get(side);
            Side otherSide = side == Side.ALLY ? Side.FOE : Side.ALLY;
            Actor other = this.standing.get(otherSide);
            if (!shown(self) || !shown(other))
                return null;
            Location from = self.home();
            Location to = other.home();
            Vector way = to.toVector().subtract(from.toVector()).setY(0);
            double distance = way.length();
            BattleStage.Body mine = this.stage.body(side.key());
            BattleStage.Body theirs = this.stage.body(otherSide.key());
            double gap = Math.max(MIN_GAP, ((mine == null ? 1 : mine.width()) + (theirs == null ? 1 : theirs.width())) / 2
                    + CONTACT_ROOM);
            if (distance <= gap)
                return null;
            Location contact = to.clone().subtract(way.multiply(gap / distance));
            contact.setYaw(from.getYaw());
            contact.setPitch(from.getPitch());
            return contact;
        }

        private double height(Side side) {
            BattleStage.Body body = this.stage.body(side.key());
            return body == null ? 0 : body.height();
        }

        /**
         * The strike: the first animation its model has from the move's category list ({@code move-physical},
         * {@code move-special}, {@code move-status}, else {@code move}; {@code {move}} there is the move's own animation,
         * {@code vinewhip}), and the move's effect leaving towards the other side's creature when the animation
         * reaches its release marker (at once without one).
         *
         * @return ticks until the release
         */
        long strike(Side side, @Nullable String move) {
            MoveData data = move == null ? null : PokeCoreAPI.get().species().findMove(move).orElse(null);
            String moment = data == null ? "move" : "move-" + data.category().name().toLowerCase(Locale.ROOT);
            String animation = this.animate(side, moment, "move", move);
            Actor attacker = this.standing.get(side);
            PokeBattleConfiguration.Creatures config = plugin.configuration().creatures();
            if (move == null || effects == null || config == null || !shown(attacker))
                return 0;
            String marker = config.moves() == null ? null : config.moves().releaseMarker();
            Duration release = animation == null || marker == null || marker.isBlank() ? Duration.ZERO
                    : this.actors.marker(attacker.entity(), animation, marker).orElse(Duration.ZERO);
            Entity entity = attacker.entity();
            Runnable play = () -> {
                // Looked up when it plays: the target may have changed or gone meanwhile
                Actor target = this.standing.get(side == Side.ALLY ? Side.FOE : Side.ALLY);
                if (entity.isValid() && shown(target))
                    effects.play(entity, target.entity(), move);
            };
            long delay = ticks(release);
            if (delay <= 0)
                play.run();
            else
                entity.getScheduler().runDelayed(plugin, _ -> play.run(), null, delay);
            return delay;
        }

        /**
         * A stat of the side's creature rose ({@code up}) or fell.
         */
        void stat(Side side, boolean up) {
            Actor creature = this.standing.get(side);
            Actor facing = this.standing.get(side == Side.ALLY ? Side.FOE : Side.ALLY);
            if (effects != null && shown(creature) && shown(facing))
                effects.stat(creature.entity(), facing.entity(), up);
        }

        private static boolean shown(@Nullable Actor actor) {
            return actor != null && actor.entity().isValid();
        }

        /**
         * The side's creature took a hit: its damage animation, its red flash and the HP it lost ({@code creature},
         * the side after the hit, against the HP last shown).
         */
        void hit(Side side, ChoiceView.Creature creature) {
            this.animate(side, "damage");
            Actor actor = this.standing.get(side);
            Integer before = this.hp.get(side);
            if (hits != null && shown(actor))
                hits.hit(this.player, actor.entity(), side == Side.ALLY,
                        before == null ? 0 : before - creature.hp());
        }

        /**
         * The field as shown after a beat: the HP the next hit is measured from (a heal or a switch in between counts).
         */
        void seen(FieldView field) {
            this.hp.put(Side.ALLY, field.ally().hp());
            this.hp.put(Side.FOE, field.foe().hp());
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
            StageMotion motion = this.moving.remove(side);
            if (motion != null)
                motion.cancel();
            Actor actor = this.standing.remove(side);
            if (actor == null)
                return;
            this.stage.body(side.key(), null);
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
         * The battle is over: the creatures go after {@code delay} ticks.
         */
        void end(long delay) {
            for (Side side : Side.values())
                this.leave(side, delay);
        }
    }
}
