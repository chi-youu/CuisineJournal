package com.chi__you.cuisinejournal.culinary;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * The single definition of "today" used by streaks, records and anti-farming.
 *
 * All daily budgets and history share the persisted daylight calendar.
 */
public final class CulinaryClock {
    private CulinaryClock() {
    }

    public static long day(Level level) {
        return level == null ? 0L : day(level.getServer());
    }

    /**
     * The authoritative day for a server, taken from the overworld so players
     * in the Nether and the End share one calendar.
     */
    public static long day(MinecraftServer server) {
        if (server == null) {
            return 0L;
        }
        return DailyCalendar.current(server).day();
    }

    /** Calendar identity used by all dated culinary records. */
    public static long displayDay(Level level) {
        return day(level);
    }
}
