package com.chi__you.cuisinejournal.food;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class CuisineFoodConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> FOOD_SYSTEM;
    public static final ForgeConfigSpec.IntValue MAX_FOOD_SLOTS;
    public static final ForgeConfigSpec.IntValue BASE_TEMPORARY_SLOTS;
    public static final ForgeConfigSpec.IntValue FOOD_SECONDS_PER_NUTRITION;
    public static final ForgeConfigSpec.IntValue DRINK_DURATION_SECONDS;
    public static final ForgeConfigSpec.BooleanValue AUTO_FOOD_SLOTS;
    public static final ForgeConfigSpec.DoubleValue ONE_SLOT_SCORE_LIMIT;
    public static final ForgeConfigSpec.IntValue TWO_SLOT_NUTRITION_LIMIT;
    public static final ForgeConfigSpec.DoubleValue TWO_SLOT_SIZE_LIMIT;
    public static final ForgeConfigSpec.DoubleValue EAT_AGAIN_PERCENTAGE;
    public static final ForgeConfigSpec.IntValue STARTING_HEARTS;
    public static final ForgeConfigSpec.IntValue MAX_FOOD_HEARTS;
    public static final ForgeConfigSpec.IntValue REGEN_DELAY_TICKS;
    public static final ForgeConfigSpec.DoubleValue REGEN_SPEED_MODIFIER;
    public static final ForgeConfigSpec.DoubleValue DRINK_BONUS;
    public static final ForgeConfigSpec.BooleanValue PASS_TICKS_DURING_NIGHT;
    public static final ForgeConfigSpec.BooleanValue PRESERVE_NATIVE_EFFECTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TWO_SLOT_ITEMS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> THREE_SLOT_ITEMS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALCOHOL_ITEMS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        FOOD_SYSTEM = builder.comment(
                        "Which food system to use.",
                        "  valheim: Valheim-style food slots, health from food, the food tooltip panel and",
                        "           food HUD; vanilla hunger and saturation are turned off.",
                        "  vanilla: vanilla hunger/saturation, vanilla tooltips (AppleSkin works);",
                        "           the journal, cooking mastery and stars still work.",
                        "Takes effect after rejoining the world or /reload. Cannot use valheim while",
                        "sol_valheim is installed.")
                .define("foodSystem", "valheim",
                        value -> "valheim".equals(value) || "vanilla".equals(value));
        MAX_FOOD_SLOTS = builder.comment("Number of ordinary food slots.")
                .defineInRange("maxFoodSlots", 2, 1, 12);
        BASE_TEMPORARY_SLOTS = builder.comment("Base temporary food capacity before growth and effects; foods retain their ordinary slot cost.")
                .defineInRange("baseTemporaryFoodSlots", 0, 0, 64);
        AUTO_FOOD_SLOTS = builder.comment("Infer food slot cost from unflavored nutrition/saturation when no explicit slot override exists.")
                .define("automaticFoodSlots", true);
        ONE_SLOT_SCORE_LIMIT = builder.comment("One-slot maximum: nutrition * (0.75 + 0.25 * adjusted saturation).")
                .defineInRange("oneSlotScoreLimit", 10.0, 0.0, 10000.0);
        TWO_SLOT_NUTRITION_LIMIT = builder.comment("Three slots require nutrition above this AND size above twoSlotSizeLimit.")
                .defineInRange("twoSlotNutritionLimit", 18, 0, 1000);
        TWO_SLOT_SIZE_LIMIT = builder.comment("Three-slot size threshold: nutrition * (0.5 + 0.5 * adjusted saturation).")
                .defineInRange("twoSlotSizeLimit", 16.2, 0.0, 10000.0);
        FOOD_SECONDS_PER_NUTRITION = builder.comment("Ordinary food lifetime in seconds per nutrition point; saturation does not affect time.")
                .defineInRange("foodSecondsPerNutrition", 60, 1, 3600);
        DRINK_DURATION_SECONDS = builder.comment("Default drink lifetime in seconds, independent of nutrition and saturation.")
                .defineInRange("drinkDurationSeconds", 600, 1, 2_592_000);
        EAT_AGAIN_PERCENTAGE = builder.comment("Remaining fraction at which the same food can be eaten again.",
                        "The last 1200 ticks are always eligible, matching the current SOL rules.")
                .defineInRange("eatAgainPercentage", 0.0, 0.0, 1.0);
        STARTING_HEARTS = builder.comment("Base maximum health in hearts before food bonuses.")
                .defineInRange("startingHearts", 5, 1, 1000);
        MAX_FOOD_HEARTS = builder.comment("Maximum health in hearts after food bonuses.")
                .defineInRange("maxFoodHearts", 100, 1, 1000);
        REGEN_DELAY_TICKS = builder.comment("Ticks since the last attack or incoming hit before food regeneration starts.")
                .defineInRange("regenDelayTicks", 120, 0, 72000);
        REGEN_SPEED_MODIFIER = builder.comment("Multiplier on the five-tick regeneration interval.")
                .defineInRange("regenSpeedModifier", 1.0, 0.2, 100.0);
        DRINK_BONUS = builder.comment("Bonus applied to combined food and drink nutrition and regeneration.")
                .defineInRange("drinkBonus", 0.1, 0.0, 100.0);
        PASS_TICKS_DURING_NIGHT = builder.comment("Reduce food time when sleeping through the night.")
                .define("passTicksDuringNight", true);
        PRESERVE_NATIVE_EFFECTS = builder.comment(
                "Keep native food effects and their original tooltip entries.")
                .define("preserveNativeFoodEffects", true);
        TWO_SLOT_ITEMS = builder.comment("Item IDs that occupy two food slots.")
                .defineListAllowEmpty("twoSlotItems", List.of(), CuisineFoodConfig::validId);
        THREE_SLOT_ITEMS = builder.comment("Item IDs that occupy three food slots. Overrides twoSlotItems.")
                .defineListAllowEmpty("threeSlotItems", List.of(), CuisineFoodConfig::validId);
        ALCOHOL_ITEMS = builder.comment("Additional alcohol item IDs; all enter the drink slot.")
                .defineListAllowEmpty("alcoholItems", List.of(), CuisineFoodConfig::validId);
        SPEC = builder.build();
    }

    private CuisineFoodConfig() {
    }

    /** True when the Valheim-style food system is selected (ignoring mod conflicts). */
    public static boolean cuisineFoodSelected() {
        return "valheim".equals(FOOD_SYSTEM.get());
    }

    private static boolean validId(Object value) {
        return value instanceof String id && ResourceLocation.tryParse(id) != null;
    }

    public static int maxFoodSlots() {
        return MAX_FOOD_SLOTS.get();
    }

    public static int slotsFor(net.minecraft.world.item.ItemStack stack) {
        var properties = stack.getFoodProperties(null);
        return slotsFor(stack, properties == null ? 0 : properties.getNutrition(),
                properties == null ? 0 : properties.getSaturationModifier());
    }

    public static int slotsFor(net.minecraft.world.item.ItemStack stack, int nutrition, float saturation) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (THREE_SLOT_ITEMS.get().contains(id.toString())) {
            return 3;
        }
        if (TWO_SLOT_ITEMS.get().contains(id.toString())) {
            return 2;
        }
        if (stack.is(CuisineFoodTags.THREE_SLOT)) {
            return 3;
        }
        if (stack.is(CuisineFoodTags.TWO_SLOT)) {
            return 2;
        }
        return AUTO_FOOD_SLOTS.get() ? CuisineFoodMath.automaticSlots(nutrition, saturation,
                ONE_SLOT_SCORE_LIMIT.get(), TWO_SLOT_NUTRITION_LIMIT.get(), TWO_SLOT_SIZE_LIMIT.get()) : 1;
    }
}
