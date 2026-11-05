package com.astralrealms.poke.battle.ui.hud;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.astralHologram.api.hud.HudView;
import com.astralrealms.astralHologram.configuration.Easing;
import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;
import com.astralrealms.poke.battle.ui.BattleCue;
import com.astralrealms.poke.battle.ui.BattleStage;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Moves a battle HUD's seat around the stage: a shot for each cue of an update, one after the other, then back to
 * {@code idle} where the player chooses. A run of shots lasts at most {@code max-sequence} (GDD: 6 s) and can be
 * skipped; the HUD glides with the camera, so it stays on screen.
 * <p>
 * Player's thread only, like the HUD.
 */
final class BattleCamera {

    private static final String IDLE = "idle";

    private final Plugin plugin;
    private final Player player;
    private final BattleStage stage;
    private final Runnable onRest;
    private final Deque<PokeBattleConfiguration.Shot> queue = new ArrayDeque<>();

    private @Nullable HudView view;
    private @Nullable ScheduledTask next;
    private boolean resting;
    private boolean stopped;

    /**
     * @param onRest run when a run of shots is over (or skipped) and the camera has landed on {@code idle}:
     *               elements shown during a glide would not follow it as smoothly, and could not be
     *               clicked before it lands anyway
     */
    BattleCamera(Plugin plugin, Player player, BattleStage stage, Runnable onRest) {
        this.plugin = plugin;
        this.player = player;
        this.stage = stage;
        this.onRest = onRest;
    }

    /**
     * Whether shots are playing or waiting: the menu waits for the camera to rest.
     */
    boolean busy() {
        return !this.resting;
    }

    /**
     * The HUD is open: plays what came before it, or settles on {@code idle}.
     */
    void attach(HudView view) {
        this.view = view;
        this.playNext();
    }

    /**
     * Queues a shot for each cue that has one, as long as the run stays within {@code max-sequence}.
     */
    void play(List<BattleCue> cues) {
        long budget = this.stage.camera().maxSequence().toMillis() / 50 - this.queued();
        boolean added = false;
        for (BattleCue cue : cues) {
            PokeBattleConfiguration.Shot shot = this.stage.camera().shots().get(cue.key());
            if (shot == null)
                continue;
            long length = Math.max(0, shot.glide()) + Math.max(0, shot.hold());
            if (length > budget)
                break;
            budget -= length;
            this.queue.addLast(shot);
            added = true;
        }
        if (!added)
            return;
        // Resting, nothing is scheduled; otherwise the shot playing (or the opening HUD) picks the queue up
        boolean wasResting = this.resting;
        this.resting = false;
        if (wasResting)
            this.playNext();
    }

    /**
     * Drops what is left of the run and goes back to {@code idle} at once.
     */
    void skip() {
        if (this.resting || this.stopped)
            return;
        this.queue.clear();
        this.cancel();
        this.rest(0);
    }

    /**
     * Stops moving the camera (the HUD is closing).
     */
    void stop() {
        this.stopped = true;
        this.queue.clear();
        this.cancel();
    }

    private void playNext() {
        this.next = null;
        if (this.stopped || this.view == null)
            return;
        PokeBattleConfiguration.Shot shot = this.queue.pollFirst();
        if (shot == null) {
            if (!this.resting) {
                PokeBattleConfiguration.Shot idle = this.stage.camera().shots().get(IDLE);
                this.rest(idle == null ? 0 : Math.max(0, idle.glide()));
            }
            return;
        }
        this.resting = false;
        this.move(shot, Math.max(0, shot.glide()));
        long ticks = Math.max(1, Math.max(0, shot.glide()) + Math.max(0, shot.hold()));
        this.next = this.player.getScheduler().runDelayed(this.plugin, _ -> this.playNext(), null, ticks);
    }

    private void rest(int glide) {
        PokeBattleConfiguration.Shot idle = this.stage.camera().shots().get(IDLE);
        if (idle == null || glide <= 0) {
            if (idle != null)
                this.move(idle, 0);
            this.settle();
            return;
        }
        this.move(idle, glide);
        // Shots queued while it glides back play from where it is
        this.next = this.player.getScheduler().runDelayed(this.plugin, _ -> {
            this.next = null;
            if (this.queue.isEmpty())
                this.settle();
            else
                this.playNext();
        }, null, glide);
    }

    private void settle() {
        this.resting = true;
        this.onRest.run();
    }

    private void move(PokeBattleConfiguration.Shot shot, int glide) {
        HudView view = this.view;
        if (view != null && view.open())
            view.moveSeat(this.stage.eye(shot), glide, Easing.EASE_IN_OUT);
    }

    private long queued() {
        return this.queue.stream()
                .mapToLong(shot -> Math.max(0, shot.glide()) + Math.max(0, shot.hold()))
                .sum();
    }

    private void cancel() {
        if (this.next != null) {
            this.next.cancel();
            this.next = null;
        }
    }
}
