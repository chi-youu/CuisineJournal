package com.chi__you.cuisinejournal.food;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/** Distinguish real stack food data from optional mods' non-food sentinels. */
public final class CuisineEdibility {
    private CuisineEdibility() {}

    public static boolean isEdible(ItemStack stack) {
        return !stack.isEmpty() && isFood(stack.getFoodProperties(null));
    }

    public static boolean isFood(FoodProperties properties) {
        if (properties == null) return false;
        return ModList.get() == null || !ModList.get().isLoaded("youkaishomecoming")
                || YoukaiFood.isFood(properties);
    }

    private static final class YoukaiFood {
        private static final FoodProperties NONE = readSentinel(
                "dev.xkmc.youkaishomecoming.content.item.fluid.SlipBottleItem");
        static boolean isFood(FoodProperties properties) {
            return properties != NONE;
        }
    }

    static FoodProperties readSentinel(String hostClass) {
        try {
            Object value = Class.forName(hostClass).getField("NONE").get(null);
            return value instanceof FoodProperties food ? food : null;
        } catch (ReflectiveOperationException | LinkageError failure) {
            com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn(
                    "Cannot read optional non-food sentinel {}; keeping other foods available", hostClass, failure);
            return null;
        }
    }
}
