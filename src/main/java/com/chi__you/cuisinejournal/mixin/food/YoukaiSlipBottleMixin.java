package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.CuisineNativeFoodEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.xkmc.youkaishomecoming.content.item.fluid.SlipBottleItem", remap = false)
public abstract class YoukaiSlipBottleMixin {
    @Inject(method = "getFoodProperties", at = @At("RETURN"), cancellable = true, remap = false)
    private void cuisinejournal$filterDynamicFood(ItemStack stack, LivingEntity entity,
            CallbackInfoReturnable<FoodProperties> cir) {
        if (!com.chi__you.cuisinejournal.food.CuisineGameplayRules.nativeEffectsEnabled()
                && com.chi__you.cuisinejournal.food.CuisineAlcohol.isAlcohol(stack)) {
            cir.setReturnValue(CuisineNativeFoodEffects.filter(stack,
                    cir.getReturnValue(), false));
        }
    }
}
