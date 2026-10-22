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
        return remaining(now, delay, warmHeart) == 0;
    }

    public int remaining(long now, int delay, boolean warmHeart) {
        if (now < protectedUntil || lastCombat == Long.MIN_VALUE) return 0;
        int effective = warmHeart ? (Math.max(0, delay) + 1) / 2 : Math.max(0, delay);
        if (now < lastCombat) return Math.max(1, effective);
        return (int) Math.max(0, effective - (now - lastCombat));
    }
}
