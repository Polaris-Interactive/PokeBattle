package com.astralrealms.poke.battle.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.astralrealms.poke.core.mechanics.ExperienceCalculator;
import com.astralrealms.poke.core.mechanics.progression.ExperienceGain;
import com.astralrealms.poke.core.model.stat.StatSpread;

import lombok.experimental.UtilityClass;

/**
 * Experience, EVs and friendship a side earns from the opponents it defeated. Every creature that fought a defeated
 * opponent and is still standing at the end gets the full experience for it (modern rule: no split between
 * participants) and the opponent's EV yield.
 */
@UtilityClass
public class RewardCalculator {

    /**
     * @param experienceMultiplier product of every bonus (format, events...), 1.0 when none
     * @param friendship           friendship each rewarded creature gains for the win
     */
    public record Rules(double experienceMultiplier, boolean effortValues, int friendship) {
    }

    /**
     * A creature of the rewarded side.
     *
     * @param standing still standing when the battle ended
     */
    public record Ally(UUID pokemonId, long version, int level, boolean standing) {
    }

    /**
     * A defeated opponent's yield.
     */
    public record Foe(int baseExperience, int level, StatSpread evYield) {
    }

    /**
     * @param allies by label
     * @param foes   by label
     * @return one gain per rewarded creature, in the order of {@code allies}
     */
    public static List<ExperienceGain> gains(List<BattleTracker.Defeat> defeats, Map<String, Ally> allies,
                                             Map<String, Foe> foes, Rules rules) {
        Map<String, int[]> experience = new LinkedHashMap<>();
        Map<String, StatSpread> evs = new LinkedHashMap<>();
        allies.keySet().forEach(label -> {
            experience.put(label, new int[1]);
            evs.put(label, StatSpread.ZERO);
        });

        for (BattleTracker.Defeat defeat : defeats) {
            Foe foe = foes.get(defeat.label());
            if (foe == null)
                continue;
            for (String label : defeat.participants()) {
                Ally ally = allies.get(label);
                if (ally == null || !ally.standing())
                    continue;
                experience.get(label)[0] += ExperienceCalculator.gain(foe.baseExperience(), foe.level(), ally.level(),
                        1, rules.experienceMultiplier());
                if (rules.effortValues())
                    evs.put(label, add(evs.get(label), foe.evYield()));
            }
        }

        List<ExperienceGain> gains = new ArrayList<>();
        allies.forEach((label, ally) -> {
            int gained = experience.get(label)[0];
            if (gained <= 0)
                return;
            gains.add(new ExperienceGain(ally.pokemonId(), ally.version(), gained, evs.get(label), rules.friendship()));
        });
        return gains;
    }

    private static StatSpread add(StatSpread a, StatSpread b) {
        return new StatSpread(a.hp() + b.hp(), a.atk() + b.atk(), a.def() + b.def(), a.spa() + b.spa(),
                a.spd() + b.spd(), a.spe() + b.spe());
    }
}
