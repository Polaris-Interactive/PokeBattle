package com.astralrealms.poke.battle.api.model;

/**
 * What a battle allows the player besides moves and switches, and how much experience a win is worth. The format
 * must allow the same choices (the worker refuses what its format does not allow).
 *
 * @param bag                  items from the bag
 * @param capture              throwing balls (only ever at a wild creature)
 * @param flee                 running away
 * @param experienceMultiplier on top of the server's own multiplier, 1.0 for none
 */
public record BattleRules(boolean bag, boolean capture, boolean flee, double experienceMultiplier) {

    public static final BattleRules WILD = new BattleRules(true, true, true, 1.0);
    public static final BattleRules TRAINER = new BattleRules(true, false, false, 1.5);
    public static final BattleRules GYM = new BattleRules(false, false, false, 1.5);

    public BattleRules {
        if (!(experienceMultiplier >= 0) || Double.isInfinite(experienceMultiplier))
            throw new IllegalArgumentException("Invalid experience multiplier " + experienceMultiplier);
    }
}
