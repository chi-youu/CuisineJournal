package com.chi__you.cuisinejournal.food;

/** Combat grace clears the old timer and ignores hits until its exclusive deadline. */
public final class FoodCombatTiming {
    public static final int SATURATION_GRACE_TICKS = 120;
    private long lastCombat = Long.MIN_VALUE;
    private long protectedUntil = Long.MIN_VALUE;

    public void enter(long now) {
        if (now >= protectedUntil) lastCombat = now;
    }

    public void saturation(long now) {
        lastCombat = Long.MIN_VALUE;
        protectedUntil = now + SATURATION_GRACE_TICKS;
    }

    public boolean ready(long now, int delay, boolean warmHeart) {
        return now < protectedUntil || CuisineFoodMath.combatRecoveryReady(now, lastCombat,
                warmHeart ? (Math.max(0, delay) + 1) / 2 : delay);
    }
}
