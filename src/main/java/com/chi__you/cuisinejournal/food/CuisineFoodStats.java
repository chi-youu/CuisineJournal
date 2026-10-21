package com.chi__you.cuisinejournal.food;

import net.minecraft.world.entity.LivingEntity;

/** SOL-style food totals, without modifying player attributes or health. */
public final class CuisineFoodStats {
    private CuisineFoodStats() {
    }

    public static float maxHealth(CuisineFoodState state) {
        return maxHealth(state, null);
    }

    public static float maxHealth(CuisineFoodState state, LivingEntity player) {
        float foodNutrition = 0.0F;
        for (CuisineFoodEntry entry : state.foods()) {
            CuisineFoodDefinition food = CuisineFoodService.definition(entry.stack());
            if (food != null) {
                foodNutrition += food.healthPoints();
            }
        }
        float maximum= CuisineFoodMath.maxHealth(CuisineFoodConfig.STARTING_HEARTS.get(),
                CuisineFoodConfig.MAX_FOOD_HEARTS.get(), foodNutrition, 0.0F,
                state.drink() == null ? 0.0F : drinkBonus(state.drink(), player),
                state.drink() != null);
        return Math.max(1,maximum+(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                ?(float)com.chi__you.cuisinejournal.skills.CookingSkills.value(serverPlayer,"health"):0));
    }

    /** Amount healed on each regeneration pulse after the configured delay. */
    public static float healPerPulse(CuisineFoodState state) {
        return healPerPulse(state, null);
    }

    public static float healPerPulse(CuisineFoodState state, LivingEntity player) {
        float foodRegen = 0.0F;
        for (CuisineFoodEntry entry : state.foods()) {
            foodRegen += contribution(entry);
        }
        return CuisineFoodMath.healPerPulse(foodRegen, 0.0F,
                state.drink() == null ? 0.0F : drinkBonus(state.drink(), player), state.drink() != null);
    }

    public static int regenIntervalTicks() {
        return Math.max(1, Math.round(5.0F * CuisineFoodConfig.REGEN_SPEED_MODIFIER.get().floatValue()));
    }
    public static float displayedRegen(float nominal,int interval){return nominal*5F/Math.max(1,interval);}
    public static float displayedRegen(CuisineFoodDefinition food){return displayedRegen(food.regenPerSecond(),regenIntervalTicks());}

    public static float temporaryHealPerPulse(CuisineFoodState state, LivingEntity player) {
        float regen = 0;
        for (CuisineFoodEntry entry : state.temporaryFoods()) regen += contribution(entry);
        float bonus = state.drink() == null ? 0 : drinkBonus(state.drink(), player);
        return regen * (1 + bonus) / 20F;
    }

    private static float contribution(CuisineFoodEntry entry) {
        CuisineFoodDefinition food = CuisineFoodService.definition(entry.stack());
        return food == null ? 0.0F : food.regenPerSecond() * 5.0F;
    }

    private static float drinkBonus(CuisineFoodEntry entry, LivingEntity player) {
        return drinkBonus(entry.stack(), player);
    }

    public static float drinkBonus(net.minecraft.world.item.ItemStack stack, LivingEntity player) {
        var sourceId = CuisineAlcohol.sourceId(stack);
        var values = CuisineFoodOverrides.get(
                sourceId == null ? net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem()) : sourceId);
        float bonus = values != null && values.drinkBonus() >= 0.0F
                ? values.drinkBonus() : CuisineFoodConfig.DRINK_BONUS.get().floatValue();
        // Flavor bonuses add percentage points (common + drink-only modifiers of the stamped stars).
        bonus = (float) Math.min(Float.MAX_VALUE,
                CuisineFlavorEffects.resolveStack(stack, true).modifiers().drinkBonus(bonus));
        return bonus;
    }
}
