package com.astralrealms.poke.battle.ui.stage;

import org.bukkit.entity.Entity;

/**
 * Plays what a move looks like between the two creatures on stage. The only implementation is
 * {@link AstralSkillEffects}, loaded when AstralSkill is: nothing else in PokeBattle names an AstralSkill type.
 */
public interface MoveEffects {

    /**
     * {@code attacker} uses {@code move} on {@code target}. Called on the main thread.
     *
     * @return false when the move has no effect to play
     */
    boolean play(Entity attacker, Entity target, String move);

    /**
     * A stat of {@code creature} rose ({@code up}) or fell; {@code facing} is the creature across the stage.
     *
     * @return false when there is no effect to play
     */
    boolean stat(Entity creature, Entity facing, boolean up);
}
