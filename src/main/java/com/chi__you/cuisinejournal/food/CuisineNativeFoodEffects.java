package com.chi__you.cuisinejournal.food;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Builds a FoodProperties copy while preserving all non-effect food behavior. */
public final class CuisineNativeFoodEffects {
    private CuisineNativeFoodEffects() {
    }

    public static FoodProperties filter(Item item, FoodProperties original, boolean preserve) {
        return filter(new ItemStack(item), original, preserve);
    }

    public static FoodProperties filter(ItemStack stack, FoodProperties original, boolean preserve) {
        if (original == null || preserve
                || original.getEffects().isEmpty()) {
            return original;
        }
        FoodProperties.Builder builder = new FoodProperties.Builder()
                .nutrition(original.getNutrition())
                .saturationMod(original.getSaturationModifier());
        if (original.isMeat()) builder.meat();
        if (original.canAlwaysEat()) builder.alwaysEat();
        if (original.isFastFood()) builder.fast();
        return builder.build();
    }
}
