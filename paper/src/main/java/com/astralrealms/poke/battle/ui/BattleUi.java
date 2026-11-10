package com.astralrealms.poke.battle.ui;

import java.util.UUID;

import org.bukkit.entity.Player;

import com.astralrealms.poke.battle.api.model.BattleOutcome;

/**
 * How a player sees and plays their battle: the choice dialog and the chat ({@link DialogUi}), or the HUD drawn in
 * front of them. The battle calls it from the player's thread, in the order things happened; {@link PacedUi} plays
 * an update's beats one after the other and holds the calls that come after them.
 */
public interface BattleUi {

    /**
     * The battle started, or the player came back to it (rejoined, {@code /combat}). Does nothing if it is already
     * shown.
     */
    void open(Player player, FieldView field);

    /**
     * How long a beat takes here: {@link #cue} first, {@link #update} {@code lead} ticks later, the next beat
     * {@code hold} ticks after that. Asked before the beat plays.
     */
    default Pace pace(Player player, BattleBeat beat) {
        return Pace.NONE;
    }

    /**
     * A beat starts: shows where it happens (the camera heads there). Nothing changes on the field yet.
     */
    default void cue(Player player, BattleBeat beat) {
    }

    /**
     * The beat shows: the field after it, its log lines.
     */
    void update(Player player, BattleBeat beat);

    /**
     * Every beat so far has played: back to where the player chooses.
     */
    default void idle(Player player) {
    }

    /**
     * The player asked to skip what is still to play (F on the HUD).
     */
    default void skip(Player player) {
    }

    /**
     * A choice to make.
     */
    void choose(Player player, ChoiceView view);

    /**
     * The choice left: nothing to do until the turn is played.
     */
    void waiting(Player player);

    /**
     * How battle {@code battleId} ended, told right before {@link #close}: shown where the battle is, when there is
     * such a place.
     */
    default void outcome(Player player, UUID battleId, BattleOutcome outcome) {
    }

    /**
     * Runs {@code action} once everything asked so far has shown: what is said about the end of a battle must not
     * come before the player has seen it.
     */
    default void then(Player player, Runnable action) {
        action.run();
    }

    /**
     * Battle {@code battleId} is over. Leaves alone what belongs to a later battle.
     */
    void close(Player player, UUID battleId);

    /**
     * The plugin is stopping: closes everything. Main thread.
     */
    void shutdown();

    /**
     * @param lead ticks from a beat's {@link #cue} to its {@link #update}
     * @param hold ticks from its update to the next beat
     */
    record Pace(long lead, long hold) {

        public static final Pace NONE = new Pace(0, 0);

        public Pace {
            lead = Math.max(0, lead);
            hold = Math.max(0, hold);
        }

        /**
         * The longer of both, part by part: two things drawn for one beat both get their time.
         */
        public Pace with(Pace other) {
            return new Pace(Math.max(this.lead, other.lead), Math.max(this.hold, other.hold));
        }

        public long length() {
            return this.lead + this.hold;
        }
    }
}
