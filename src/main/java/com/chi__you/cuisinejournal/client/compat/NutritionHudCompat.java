package com.chi__you.cuisinejournal.client.compat;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/** AppleSkin remains optional; its display preferences also govern our overflow extension. */
public final class NutritionHudCompat {
    public record Preview(int hunger, float saturation) {}
    private static boolean warned;
    private static final java.util.Map<String, net.minecraftforge.common.ForgeConfigSpec.ConfigValue<?>> OPTIONS = new java.util.HashMap<>();
    private static java.lang.reflect.Method consume, foodValues, saturationIncrement;
    private static java.lang.reflect.Field hunger;
    private NutritionHudCompat() {}

    public static boolean saturationVisible() { return option("SHOW_SATURATION_OVERLAY", true); }

    private static boolean option(String name, boolean absent) {
        if (!ModList.get().isLoaded("appleskin")) return absent;
        try {
            var value = OPTIONS.get(name);
            if (value == null) {
                value = (net.minecraftforge.common.ForgeConfigSpec.ConfigValue<?>)
                        Class.forName("squeek.appleskin.ModConfig").getField(name).get(null);
                OPTIONS.put(name, value);
            }
            return Boolean.TRUE.equals(value.get());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warn(failure); return absent;
        }
    }

    public static Preview preview(Player player) {
        if (!ModList.get().isLoaded("appleskin") || !option("SHOW_FOOD_VALUES_OVERLAY", false)) return null;
        try {
            if (consume == null) {
                var helper = Class.forName("squeek.appleskin.helpers.FoodHelper");
                consume = helper.getMethod("canConsume", ItemStack.class, Player.class);
                foodValues = helper.getMethod("getModifiedFoodValues", ItemStack.class, Player.class);
                var valuesType = Class.forName("squeek.appleskin.api.food.FoodValues");
                hunger = valuesType.getField("hunger");
                saturationIncrement = valuesType.getMethod("getSaturationIncrement");
            }
            ItemStack stack = player.getMainHandItem();
            if (!Boolean.TRUE.equals(consume.invoke(null, stack, player))
                    && option("SHOW_FOOD_VALUES_OVERLAY_WHEN_OFFHAND", false)) stack = player.getOffhandItem();
            if (!Boolean.TRUE.equals(consume.invoke(null, stack, player))) return null;
            var values = foodValues.invoke(null, stack, player);
            return new Preview(hunger.getInt(values), ((Number) saturationIncrement.invoke(values)).floatValue());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { warn(failure); return null; }
    }

    private static void warn(Throwable failure) {
        if (warned) return;
        warned = true;
        CuisineJournalLog.LOGGER.warn("Cannot read optional AppleSkin overflow display settings", failure);
    }
}
