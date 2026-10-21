package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.CuisineNativeFoodEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ItemFoodPropertiesMixin {
    @Inject(method = "getFoodProperties", at = @At("RETURN"), cancellable = true)
    private void cuisinejournal$filterNativeEffects(CallbackInfoReturnable<FoodProperties> cir) {
        if (!com.chi__you.cuisinejournal.food.CuisineGameplayRules.nativeEffectsEnabled())
            cir.setReturnValue(CuisineNativeFoodEffects.filter((Item) (Object) this, cir.getReturnValue(), false));
    }
}
