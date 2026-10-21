package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.config.ConfigManager;
import net.minecraft.world.item.ItemStack;

public final class CuisineStarBonuses {
    private CuisineStarBonuses() {}
    public static boolean enabled() {
        var config = ConfigManager.server();
        return config.mastery.enabled && config.mastery.bonuses.enabled;
    }
    public static double healthOffset(int stars) {
        var bonuses = ConfigManager.server().mastery.bonuses;
        return enabled() && bonuses.valheimStarGrowth ? bonuses.valheimHealthOffsets.get(Math.max(0, Math.min(5, stars))) : 0;
    }
    public static double regenCoefficient(int stars) {
        var bonuses = ConfigManager.server().mastery.bonuses;
        return enabled() && bonuses.valheimStarGrowth ? bonuses.valheimRegenCoefficients.get(Math.max(0, Math.min(5, stars))) : 0.03;
    }
    public static boolean fillsCapacity(ItemStack stack) {
        return enabled() && ConfigManager.server().mastery.bonuses.valheimFiveStarCapacityFill && stars(stack) == 5;
    }
    public static int stars(ItemStack stack) {
        var config = ConfigManager.server();
        if (!config.mastery.enabled || !config.mastery.bonuses.enabled) return 0;
        return CuisineFoodQuality.stars(stack);
    }
    public static float scaled(float base, double perStar, int stars) {
        return (float) Math.min(Float.MAX_VALUE, base * (1 + perStar * Math.max(0,Math.min(5,stars))));
    }
    public static int duration(int base, double perStar, int stars) {
        return (int) Math.min(Integer.MAX_VALUE, (double)base * (1+perStar*Math.max(0,Math.min(5,stars))));
    }
}
