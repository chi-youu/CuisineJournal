package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.config.ServerConfig;
import com.chi__you.cuisinejournal.culinary.MealStamp;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/** Same product quality formula for ordinary items, placed servings and nutrition previews. */
public final class CuisineVanillaBonuses {
    private static final ThreadLocal<Boolean> ITEM_APPLICATION = new ThreadLocal<>();
    public record Nutrition(int nutrition, float saturationModifier) {}
    private CuisineVanillaBonuses() {}

    public static Nutrition calculate(int nutrition, float modifier, MealStamp stamp, double perStar,
                                      float maxStarExtra, double perfectMultiplier) {
        if (stamp == null) return new Nutrition(nutrition, modifier);
        int stars = stamp.effectiveStars();
        int adjusted = stamp.perfect() ? (int)Math.min(Integer.MAX_VALUE, Math.round(nutrition * perfectMultiplier)) : nutrition;
        float coefficient = CuisineStarBonuses.scaled(modifier, perStar, stars);
        if (stars >= 5 && adjusted > 0) coefficient += maxStarExtra / (2.0F * adjusted);
        return new Nutrition(adjusted, coefficient);
    }
    private static ServerConfig.Bonuses rules() {
        if (CuisineFoodRuntime.activeForCurrentThread()) return null;
        if (CuisineFoodRuntime.isClientThread()) {
            return ClientFoodRuleView.current().vanillaBonuses();
        }
        var config = ConfigManager.server();
        return config.mastery.enabled && config.mastery.bonuses.enabled ? config.mastery.bonuses : null;
    }
    public static Nutrition calculate(int nutrition, float coefficient, ItemStack stack) {
        if(KaleidoscopeFood.quality(stack)||LetsDoFood.mapped(stack))return new Nutrition(nutrition,coefficient);
        var rules = rules();
        return rules == null ? new Nutrition(nutrition, coefficient) : calculate(nutrition, coefficient,
                MealStamp.read(stack),rules.vanillaSaturationMultiplierPerStar,rules.vanillaMaxStarSaturation,
                rules.vanillaPerfectNutritionMultiplier);
    }
    public static FoodProperties properties(ItemStack stack, FoodProperties original) {
        // Placed hosts often read serving properties and then call eat(int,float) directly.
        if(PlacedFoodConsumption.eatingContext()&&!applyingItem())return original;
        stack=PlacedFoodConsumption.qualityStack(stack);
        if (original == null || MealStamp.read(stack) == null) return original;
        var value = calculate(original.getNutrition(), original.getSaturationModifier(), stack);
        if (value.nutrition() == original.getNutrition() && value.saturationModifier() == original.getSaturationModifier()) return original;
        var builder = new FoodProperties.Builder().nutrition(value.nutrition()).saturationMod(value.saturationModifier());
        if (original.isMeat()) builder.meat();
        if (original.canAlwaysEat()) builder.alwaysEat();
        if (original.isFastFood()) builder.fast();
        for (var effect : original.getEffects()) builder.effect(() -> new net.minecraft.world.effect.MobEffectInstance(effect.getFirst()),effect.getSecond());
        return builder.build();
    }
    public static void itemApplication(Runnable host) {
        Boolean previous = ITEM_APPLICATION.get(); ITEM_APPLICATION.set(true);
        try { host.run(); }
        finally { if (previous == null) ITEM_APPLICATION.remove(); else ITEM_APPLICATION.set(previous); }
    }
    public static boolean applyingItem() { return Boolean.TRUE.equals(ITEM_APPLICATION.get()); }
    public static FoodProperties itemProperties(ItemStack stack,net.minecraft.world.entity.LivingEntity entity) {
        FoodProperties[] result=new FoodProperties[1];
        itemApplication(()->result[0]=stack.getFoodProperties(entity));
        return result[0];
    }
}
