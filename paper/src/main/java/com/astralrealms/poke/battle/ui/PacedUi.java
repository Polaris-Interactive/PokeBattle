package com.astralrealms.poke.battle.ui;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.battle.PokeBattle;
import com.astralrealms.poke.battle.api.model.BattleOutcome;
import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Plays each player's battle as a timeline: an update's beats one after the other, each taking the time the UI
 * asks for ({@link BattleUi#pace}), so the HP bars, the log and the creatures change when the camera shows the
 * moment, not all at once. Calls that come after an update (a choice, the end) wait for its beats.
 * <p>
 * A run of beats lasts at most {@code hud.camera.max-sequence} (GDD: 6 s): what does not fit shows at once, as
 * when the player skips (F).
 * <p>
 * Main thread only (the battle calls the UI on the player's thread, and PokeBattle does not run on Folia).
 */
public class PacedUi implements BattleUi {

    private final PokeBattle plugin;
    private final BattleUi ui;
    private final Map<UUID, Timeline> timelines = new HashMap<>();

    public PacedUi(PokeBattle plugin, BattleUi ui) {
        this.plugin = plugin;
        this.ui = ui;
    }

    @Override
    public void open(Player player, FieldView field) {
        this.timeline(player).call(() -> this.ui.open(player, field));
    }

    @Override
    public void update(Player player, BattleBeat beat) {
        this.timeline(player).beat(beat);
    }

    @Override
    public void skip(Player player) {
        Timeline timeline = this.timelines.get(player.getUniqueId());
        if (timeline != null)
            timeline.skip();
    }

    @Override
    public void choose(Player player, ChoiceView view) {
        this.timeline(player).call(() -> this.ui.choose(player, view));
    }

    @Override
    public void waiting(Player player) {
        this.timeline(player).call(() -> this.ui.waiting(player));
    }

    @Override
    public void outcome(Player player, UUID battleId, BattleOutcome outcome) {
        this.timeline(player).call(() -> this.ui.outcome(player, battleId, outcome));
    }

    @Override
    public void then(Player player, Runnable action) {
        this.timeline(player).call(() -> this.ui.then(player, action));
    }

    @Override
    public void close(Player player, UUID battleId) {
        this.timeline(player).call(() -> this.ui.close(player, battleId));
    }

    @Override
    public void shutdown() {
        this.timelines.values().forEach(Timeline::cancel);
        this.timelines.clear();
        this.ui.shutdown();
    }

    private Timeline timeline(Player player) {
        Timeline timeline = this.timelines.get(player.getUniqueId());
        // A player who left and came back is a new Player object: their old timeline was flushed when they left
        if (timeline == null || timeline.player != player) {
            if (timeline != null)
                timeline.cancel();
            timeline = new Timeline(player);
            this.timelines.put(player.getUniqueId(), timeline);
        }
        return timeline;
    }

    private long maxSequence() {
        PokeBattleConfiguration.Hud hud = this.plugin.configuration().hud();
        Duration max = hud == null || hud.camera() == null ? null : hud.camera().maxSequence();
        return max == null ? Long.MAX_VALUE : max.toMillis() / 50;
    }

    private sealed interface Step {
    }

    private record Beat(BattleBeat beat) implements Step {
    }

    private record Call(Runnable action) implements Step {
    }

    /**
     * One player's steps still to play.
     */
    private final class Timeline {

        private final Player player;
        private final Deque<Step> steps = new ArrayDeque<>();
        private @Nullable ScheduledTask task;
        /** The beat cued whose update has not shown yet. */
        private @Nullable BattleBeat cued;
        /** Ticks spent on beats since the timeline was last idle. */
        private long run;
        private boolean played;
        private boolean skipping;
        private boolean draining;

        Timeline(Player player) {
            this.player = player;
        }

        void beat(BattleBeat beat) {
            this.steps.addLast(new Beat(beat));
            this.drain();
        }

        void call(Runnable action) {
            this.steps.addLast(new Call(action));
            this.drain();
        }

        /**
         * Shows what is left of this run at once.
         */
        void skip() {
            if (this.task == null && this.cued == null)
                return;
            this.skipping = true;
            this.cancelTask();
            this.showCued();
            this.drain();
        }

        void cancel() {
            this.cancelTask();
            this.steps.clear();
            this.cued = null;
        }

        private void drain() {
            // A step that calls back into the UI must not start the next one under itself
            if (this.draining || this.task != null)
                return;
            this.draining = true;
            try {
                while (this.task == null) {
                    Step step = this.steps.pollFirst();
                    if (step == null) {
                        this.rest();
                        return;
                    }
                    switch (step) {
                        case Call call -> call.action().run();
                        case Beat beat -> this.play(beat.beat());
                    }
                }
            } finally {
                this.draining = false;
            }
        }

        private void play(BattleBeat beat) {
            Pace pace = this.skipping ? Pace.NONE : ui.pace(this.player, beat);
            if (pace.length() == 0 || this.run + pace.length() > maxSequence()) {
                ui.update(this.player, beat);
                this.played = true;
                return;
            }
            this.run += pace.length();
            this.played = true;
            ui.cue(this.player, beat);
            this.cued = beat;
            if (pace.lead() == 0) {
                this.showCued();
                this.wait(pace.hold(), this::next);
            } else {
                this.wait(pace.lead(), () -> {
                    this.showCued();
                    this.wait(pace.hold(), this::next);
                });
            }
        }

        private void showCued() {
            BattleBeat beat = this.cued;
            this.cued = null;
            if (beat != null)
                ui.update(this.player, beat);
        }

        private void next() {
            this.task = null;
            this.drain();
        }

        /**
         * Runs {@code then} in {@code ticks}; the timeline holds until then. When the player leaves, the rest shows
         * at once (closing the battle's UI among it).
         */
        private void wait(long ticks, Runnable then) {
            if (ticks <= 0) {
                this.task = null;
                then.run();
                return;
            }
            this.task = this.player.getScheduler().runDelayed(plugin, _ -> {
                this.task = null;
                then.run();
                if (this.task == null)
                    this.drain();
            }, () -> {
                this.task = null;
                this.skipping = true;
                this.showCued();
                this.drain();
            }, ticks);
            // The player is already gone: nothing waits
            if (this.task == null) {
                this.skipping = true;
                then.run();
            }
        }

        private void rest() {
            if (this.played)
                ui.idle(this.player);
            this.played = false;
            this.skipping = false;
            this.run = 0;
        }

        private void cancelTask() {
            if (this.task != null) {
                this.task.cancel();
                this.task = null;
            }
        }
    }
}
