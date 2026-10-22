package com.chi__you.cuisinejournal.client.hud;

/** Local interpolation advances only during unpaused client ticks, independent of dimension time. */
public final class CombatCountdown {
    private int remaining;
    public void accept(int ticksLeft) { remaining = Math.max(0, ticksLeft); }
    public void tick(boolean paused) { if (!paused && remaining > 0) remaining--; }
    public int remaining() { return remaining; }
    public void clear() { remaining = 0; }
}
