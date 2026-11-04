package com.astralrealms.poke.battle.protocol;

import org.jetbrains.annotations.Nullable;

/**
 * A protocol HP/status field: {@code 35/120 par}, {@code 54/100} (a percentage, for the opponent), {@code 0 fnt}.
 *
 * @param status empty when healthy, {@code fnt} when fainted
 */
public record Condition(int hp, int maxHp, String status) {

    public static @Nullable Condition parse(@Nullable String value) {
        if (value == null || value.isEmpty())
            return null;
        String[] parts = value.trim().split(" ", 2);
        String status = parts.length > 1 ? parts[1] : "";
        try {
            int slash = parts[0].indexOf('/');
            if (slash < 0)
                return new Condition(Integer.parseInt(parts[0]), 0, status);
            return new Condition(Integer.parseInt(parts[0].substring(0, slash)),
                    Integer.parseInt(parts[0].substring(slash + 1)), status);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean fainted() {
        return this.hp <= 0 || this.status.equals("fnt");
    }

    public int percent() {
        if (this.maxHp <= 0)
            return this.fainted() ? 0 : 100;
        int percent = (int) Math.round(this.hp * 100.0 / this.maxHp);
        // Never show 0% for a creature still standing
        return this.hp > 0 ? Math.max(1, percent) : 0;
    }
}
