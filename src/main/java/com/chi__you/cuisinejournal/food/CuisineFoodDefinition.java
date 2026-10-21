package com.chi__you.cuisinejournal.food;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Resolved values used by the food state, independent of an Item instance. */
public record CuisineFoodDefinition(
        int nutrition,
        float saturationModifier,
        float healthRegenModifier,
        int durationTicks,
        int slotCost,
        boolean drink,
        boolean repeatable,
        float healthPoints,
        float regenPerSecond) {

    private static final class SlotOverrides {
        private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> FOOD =
                net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.parse("cuisinejournal:food_slot_overrides"));
    }

    private static String slotType(ItemStack stack,CuisineFoodDefinitions.Values values) {
        return values!=null&&values.slotType()!=null?values.slotType():stack.is(SlotOverrides.FOOD)?"food":null;
    }

    public static CuisineFoodDefinition from(ItemStack stack) {
        var item = stack.getItem();
        var properties = stack.getFoodProperties(null);
        var itemId = BuiltInRegistries.ITEM.getKey(item);
        var sourceId = CuisineAlcohol.sourceId(stack);
        if (itemId == null || item == Items.AIR || itemId.toString().equals("youkaishomecoming:sake_bottle")
                && sourceId == null) {
            return null;
        }
        CuisineFoodDefinitions.Values values = sourceId == null ? null : CuisineFoodDefinitions.get(sourceId);
        if (values == null) {
            values = CuisineFoodDefinitions.get(itemId);
        }
        boolean alcohol = sourceId != null;
        boolean drink = !ExquisitoParfaits.matches(stack) && isDrink(slotType(stack,values), alcohol, stack.getUseAnimation());
        if (!CuisineEdibility.isFood(properties) && item != Items.CAKE) {
            return null;
        }

        CuisineFoodOverrides.FoodValues override = CuisineFoodOverrides.get(
                sourceId == null ? itemId : sourceId);
        int nutrition = override != null ? override.nutrition()
                : properties == null ? 2 : properties.getNutrition();
        float saturation = override != null ? override.saturationModifier()
                : properties == null ? 0.5F : properties.getSaturationModifier();
        float regen = override != null ? override.healthRegenModifier() : 1F;
        if (override == null) {
            if (item == Items.CAKE) {
                nutrition = 10;
                saturation = 0.7F;
            }
        }
        int duration = CuisineFoodMath.durationTicks(
                CuisineFoodConfig.FOOD_SECONDS_PER_NUTRITION.get(),
                CuisineFoodConfig.DRINK_DURATION_SECONDS.get(), nutrition, drink);
        if (values != null && values.durationSeconds() != null) {
            duration = values.durationSeconds() * 20;
        }
        var baseProperties=KaleidoscopeFood.quality(stack)?KaleidoscopeFood.baseProperties(stack):null;
        int slots = drink ? 1 : values != null && values.slotCost() != null
                ? values.slotCost() : CuisineFoodConfig.slotsFor(stack,
                baseProperties==null?nutrition:baseProperties.getNutrition(),baseProperties==null?saturation:baseProperties.getSaturationModifier());
        int stars = CuisineFoodRuntime.activeForCurrentThread() ? CuisineStarBonuses.stars(stack) : 0;
        if(KaleidoscopeFood.quality(stack)||LetsDoFood.mapped(stack))stars=2;
        var bonuses = com.chi__you.cuisinejournal.config.ConfigManager.server().mastery.bonuses;
        saturation=GrillingFoodCompat.saturation(stack,saturation);
        float health = values != null && values.health() != null ? values.health()
                : CuisineFoodMath.foodHealthPoints(nutrition, saturation, slots, CuisineStarBonuses.healthOffset(stars));
        float healing = values != null && values.regenPerSecond() != null ? values.regenPerSecond()
                : CuisineFoodMath.foodRegenPerSecond(nutrition, saturation, CuisineStarBonuses.regenCoefficient(stars)) * regen;
        if(!KaleidoscopeFood.quality(stack)&&!LetsDoFood.mapped(stack))duration = CuisineStarBonuses.duration(duration, bonuses.valheimDurationPerStar, stars);
        var modifiers=CuisineFlavorEffects.resolveStack(stack,drink).modifiers();
        if(!KaleidoscopeFood.quality(stack)&&!LetsDoFood.mapped(stack)){
            health=CuisineStarBonuses.scaled(health,bonuses.valheimHealthPerStar,stars);
            healing=CuisineStarBonuses.scaled(healing,bonuses.valheimRegenPerStar,stars);
        }
        // Fixed slot classification is deliberately resolved before flavor modifiers.
        if(!drink) {
            health=(float)Math.min(Float.MAX_VALUE,modifiers.health(health));
            healing=(float)Math.min(Float.MAX_VALUE,modifiers.regen(healing));
        }
        duration=modifiers.durationTicks(duration);
        return new CuisineFoodDefinition(nutrition, saturation, regen, duration,
                slots, drink, values != null && values.repeatable(),
                CuisineFoodMath.roundedHealthPoints(health),healing);
    }

    /** Shares slot classification with the journal without resolving nutrition or timers. */
    public static boolean isDrink(ItemStack stack) {
        if (ExquisitoParfaits.matches(stack)) return false;
        if (!CuisineEdibility.isEdible(stack)) return false;
        var sourceId = CuisineAlcohol.sourceId(stack);
        var values = sourceId == null ? null : CuisineFoodDefinitions.get(sourceId);
        if (values == null) values = CuisineFoodDefinitions.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return isDrink(slotType(stack,values), sourceId != null, stack.getUseAnimation());
    }

    static boolean isDrink(String slotType, boolean alcohol, net.minecraft.world.item.UseAnim animation) {
        return slotType != null ? slotType.equals("drink")
                : alcohol || animation == net.minecraft.world.item.UseAnim.DRINK;
    }
}
