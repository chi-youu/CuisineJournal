package com.chi__you.cuisinejournal.skills;

import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Authoritative aggregate; the Skills mod owns purchases, costs and unlocked nodes. */
public final class CookingSkills {
    public static final String ROOT = "skillBonuses";
    private CookingSkills() {}

    public static double value(CulinaryProfile profile, String key) {
        return active(profile) ? profile.skillBonuses().getDouble(key) : 0;
    }
    private static boolean active(CulinaryProfile profile) {
        return profile.skillsActive() && (com.chi__you.cuisinejournal.CuisineJournalServer.server()==null
                || profile.skillMode().equals(com.chi__you.cuisinejournal.food.CuisineFoodRuntime.active()?"valheim":"vanilla"));
    }
    public static double value(ServerPlayer player, String key) {
        var profiles = com.chi__you.cuisinejournal.culinary.ProfileManager.get();
        return profiles == null ? 0 : value(profiles.of(player), key);
    }
    public static int starCap(CulinaryProfile profile) {
        return active(profile) ? Math.max(1, Math.min(5, 1 + (int)value(profile,"stars"))) : 5;
    }
    public static double minimum(CulinaryProfile profile, double base) {
        return Math.max(0, Math.min(1, base + value(profile,"floor")));
    }
    public static double penalty(CulinaryProfile profile) {
        return Math.max(0, .01 - value(profile,"batch"));
    }
    public static int temporaryTicks(ServerPlayer player) {
        return Math.max(20, (int)Math.round((5 + value(player,"temporary_seconds")) * 20));
    }
    public static int ordinaryTicks(ServerPlayer player, int original) {
        return Math.max(20, (int)Math.round(original * Math.max(.05, 1 + value(player,"duration"))));
    }
    public static double eatMultiplier(CulinaryProfile profile) {
        return Math.max(.1, 1 + value(profile,"eat"));
    }
    public static float shieldRemaining(float shield,float damage) {
        return Math.max(0,shield-Math.max(0,damage));
    }
    public static float shieldDamage(float shield,float damage) {
        return Math.max(0,damage-Math.max(0,shield));
    }
    public static CompoundTag sum(java.util.Collection<CompoundTag> effects) {
        CompoundTag result = new CompoundTag();
        for (var effect : effects) for (String key : effect.getAllKeys()) {
            double v = effect.getDouble(key);
            if (Double.isFinite(v)) result.putDouble(key, result.getDouble(key) + v);
        }
        return result;
    }
}
