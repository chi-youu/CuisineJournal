package com.chi__you.cuisinejournal.culinary;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Shared daylight calendar for daily content, progression budgets and history. */
public final class DailyCalendar extends SavedData {
    public record Snapshot(long day, long remainingTicks) {}
    private long day;
    private long highestDate;
    private boolean initialized;
    private boolean betterTime;

    public static DailyCalendar get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DailyCalendar::load,DailyCalendar::new,
                "cuisinejournal_daily_calendar");
    }

    public static Snapshot current(MinecraftServer server) {
        if(server==null)return new Snapshot(0,24000);
        boolean better=net.minecraftforge.fml.ModList.get().isLoaded("bettertime");
        long time=server.overworld().getDayTime();
        // BetterTime 1.4 getDays(Level) uses dayTime / 24000, with rollover at tick zero.
        // This runtime-based value is used once only to preserve pre-calendar save identities.
        return get(server).observe(time,better,server.overworld().getGameTime()/24000L);
    }

    public static long date(long dayTime, boolean betterTime) {
        return Math.max(0,Math.floorDiv(dayTime,24000)+(betterTime?0:Math.floorMod(dayTime,24000)>=22000?1:0));
    }

    public static long remaining(long dayTime, boolean betterTime) {
        long time=Math.floorMod(dayTime,24000);
        long offset=betterTime?time:Math.floorMod(time+2000,24000);
        return 24000-offset;
    }

    public Snapshot observe(long dayTime,boolean better,long legacyDay) {
        long date=date(dayTime,better);
        if(!initialized) {
            // Keep the old period number on upgrade so today's saved task and dish are not rerolled.
            day=Math.max(0,legacyDay);highestDate=date;betterTime=better;initialized=true;setDirty();
        } else if(betterTime!=better) {
            highestDate=Math.max(highestDate,date);betterTime=better;setDirty();
        } else if(date>highestDate) {
            day+=date-highestDate;highestDate=date;setDirty();
        }
        return new Snapshot(day,remaining(dayTime,better));
    }

    public static DailyCalendar load(CompoundTag tag) {
        var calendar=new DailyCalendar();calendar.day=tag.getLong("day");calendar.highestDate=tag.getLong("highestDate");
        calendar.initialized=tag.getBoolean("initialized");calendar.betterTime=tag.getBoolean("betterTime");return calendar;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        tag.putLong("day",day);tag.putLong("highestDate",highestDate);tag.putBoolean("initialized",initialized);
        tag.putBoolean("betterTime",betterTime);return tag;
    }
}
