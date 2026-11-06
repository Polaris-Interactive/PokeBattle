package com.astralrealms.poke.battle.ui.hud;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.astralHologram.api.hud.HudView;
import com.astralrealms.astralHologram.configuration.Easing;
import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;
import com.astralrealms.poke.battle.ui.BattleCue;
import com.astralrealms.poke.battle.ui.BattleStage;
import com.astralrealms.poke.battle.ui.BattleUi;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Moves a battle HUD's seat around the stage: the shot of each beat as the timeline plays it, then back to
 * {@code idle} where the player chooses. The HUD glides with the camera, so it stays on screen.
 * <p>
 * Player's thread only, like the HUD.
 */
final class BattleCamera {

    private static final String IDLE = "idle";

    private final Plugin plugin;
    private final Player player;
    private final BattleStage stage;
    private final Runnable onRest;

    private @Nullable HudView view;
    private PokeBattleConfiguration.@Nullable Shot current;
    private @Nullable ScheduledTask landing;
    private boolean resting;
    private boolean stopped;

    /**
     * @param onRest run when the camera has landed on {@code idle}: elements shown during a glide would not follow
     *               it as smoothly, and could not be clicked before it lands anyway
     */
    BattleCamera(Plugin plugin, Player player, BattleStage stage, Runnable onRest) {
        this.plugin = plugin;
        this.player = player;
        this.stage = stage;
        this.onRest = onRest;
    }

    /**
     * Whether it is away from {@code idle} or on its way back: the menu waits for it to rest.
     */
    boolean busy() {
        return !this.resting;
    }

    /**
     * The HUD is open: the camera jumps to where it should be by now.
     */
    void attach(HudView view) {
        this.view = view;
        PokeBattleConfiguration.Shot at = this.current != null ? this.current : this.shot(IDLE);
        if (at != null)
            this.move(at, 0);
        if (this.current == null && !this.resting && this.landing == null)
            this.rest();
    }

    /**
     * The beat's time: the shot's glide before the field changes (the camera gets there), its hold after. A cue
     * without a shot leaves the camera where it is and takes no time.
     */
    BattleUi.Pace pace(@Nullable BattleCue cue) {
        PokeBattleConfiguration.Shot shot = this.shot(cue);
        return shot == null ? BattleUi.Pace.NONE : new BattleUi.Pace(shot.glide(), shot.hold());
    }

    /**
     * Heads for the cue's shot, if it has one.
     */
    void cue(@Nullable BattleCue cue) {
        PokeBattleConfiguration.Shot shot = this.shot(cue);
        if (shot == null || this.stopped)
            return;
        this.cancel();
        this.resting = false;
        this.current = shot;
        this.move(shot, Math.max(0, shot.glide()));
    }

    /**
     * Back to {@code idle}; the menu shows once it lands.
     */
    void rest() {
        if (this.resting || this.stopped || this.landing != null)
            return;
        this.current = null;
        PokeBattleConfiguration.Shot idle = this.shot(IDLE);
        int glide = idle == null ? 0 : Math.max(0, idle.glide());
        if (idle != null)
            this.move(idle, glide);
        if (glide == 0) {
            this.settle();
            return;
        }
        this.landing = this.player.getScheduler().runDelayed(this.plugin, _ -> {
            this.landing = null;
            this.settle();
        }, null, glide);
    }

    /**
     * Stops moving the camera (the HUD is closing).
     */
    void stop() {
        this.stopped = true;
        this.cancel();
    }

    private void settle() {
        this.resting = true;
        this.onRest.run();
    }

    private PokeBattleConfiguration.@Nullable Shot shot(@Nullable BattleCue cue) {
        return cue == null ? null : this.shot(cue.key());
    }

    private PokeBattleConfiguration.@Nullable Shot shot(String key) {
        PokeBattleConfiguration.Camera camera = this.stage.camera();
        return camera == null ? null : camera.shots().get(key);
    }

    private void move(PokeBattleConfiguration.Shot shot, int glide) {
        HudView view = this.view;
        if (view != null && view.open())
            view.moveSeat(this.stage.eye(shot), glide, Easing.EASE_IN_OUT);
    }

    private void cancel() {
        if (this.landing != null) {
            this.landing.cancel();
            this.landing = null;
        }
    }
}
