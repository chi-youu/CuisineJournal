package com.chi__you.cuisinejournal.culinary;

import com.chi__you.cuisinejournal.config.ServerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;

/** Daily counts belong to the cook; fractional mastery belongs to its actual dish. */
public final class SocialRewards {
    private long day = Long.MIN_VALUE;
    private long meals;
    private double xpRemainder;
    private final Map<ResourceLocation, Double> masteryRemainders = new LinkedHashMap<>();
    public record Award(int mastery, long xp, double multiplier) {}

    public Award claim(long currentDay, ResourceLocation dish, ServerConfig config) {
        if (day != currentDay) { day = currentDay; meals = 0; }
        if (meals < Long.MAX_VALUE) meals++;
        var rules = config.socialRewards;
        double rate = !rules.dailyDecay ? 1 : meals <= rules.fullMeals ? 1
                : meals <= rules.halfMeals ? rules.halfMultiplier
                : meals <= rules.quarterMeals ? rules.quarterMultiplier : rules.minimumMultiplier;
        int mastery = 0;
        long xp = 0;
        if (config.mastery.enabled && config.mastery.bonuses.masteryPerMealServedToOthers > 0) {
            double total = masteryRemainders.getOrDefault(dish, 0.0)
                    + config.mastery.bonuses.masteryPerMealServedToOthers * rate;
            mastery = (int) Math.floor(total + 1e-9);
            double remainder = Math.max(0, total - mastery);
            if (remainder < 1e-9) masteryRemainders.remove(dish);
            else masteryRemainders.put(dish, remainder);
        }
        if (config.progression.enabled && config.progression.baseXpPerMealServedToOthers > 0) {
            double total = xpRemainder + config.progression.baseXpPerMealServedToOthers * rate
                    * config.progression.xpMultiplier;
            xp = (long) Math.floor(total + 1e-9);
            xpRemainder = Math.max(0, total - xp);
        }
        return new Award(mastery, xp, rate);
    }
    public long mealsOn(long currentDay) { return day == currentDay ? meals : 0; }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("day", day); tag.putLong("meals", meals); tag.putDouble("xpRemainder", xpRemainder);
        CompoundTag mastery = new CompoundTag();
        masteryRemainders.forEach((id, value) -> mastery.putDouble(id.toString(), value));
        tag.put("masteryRemainders", mastery); return tag;
    }
    public static SocialRewards load(CompoundTag tag) {
        SocialRewards result = new SocialRewards();
        if (tag.contains("day")) result.day = tag.getLong("day");
        result.meals = Math.max(0, tag.getLong("meals"));
        result.xpRemainder = fraction(tag.getDouble("xpRemainder"));
        CompoundTag mastery = tag.getCompound("masteryRemainders");
        for (String key : mastery.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            double value = fraction(mastery.getDouble(key));
            if (id != null && value > 0) result.masteryRemainders.put(id, value);
        }
        return result;
    }
    private static double fraction(double value) {
        return Double.isFinite(value) && value >= 0 && value < 1 ? value : 0;
    }
}
