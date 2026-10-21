package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.CuisineVanillaBonuses;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemStack.class)
public abstract class VanillaFoodPropertiesMixin {
    /** Implements Forge's inherited default while retaining each Item's own stack-aware lookup. */
    public FoodProperties getFoodProperties(LivingEntity entity) {
        ItemStack stack=(ItemStack)(Object)this;
        var original=stack.getItem().getFoodProperties(stack,entity);
        original=com.chi__you.cuisinejournal.food.DelightPlacedFoods.properties(stack.getItem(),original);
        original=com.chi__you.cuisinejournal.food.AbnormalsPlacedFoods.properties(stack,original);
        original=com.chi__you.cuisinejournal.food.KaleidoscopeFood.properties(stack,original);
        original=com.chi__you.cuisinejournal.food.LetsDoFood.properties(stack,original);
        original=com.chi__you.cuisinejournal.food.ModFoodCompat.properties(stack,original);
        original=com.chi__you.cuisinejournal.food.CakeFoodCompat.properties(stack,original);
        if (!com.chi__you.cuisinejournal.food.CuisineGameplayRules.nativeEffectsEnabled())
            original=com.chi__you.cuisinejournal.food.CuisineNativeFoodEffects.filter(stack,original,false);
        return CuisineVanillaBonuses.properties(stack,original);
    }
}
