package com.chi__you.cuisinejournal.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Suppress Quark's food icons while retaining its saturation descriptions. */
@Pseudo
@Mixin(targets = "org.violetmoon.quark.content.client.tooltip.FoodTooltips", remap = false)
public abstract class QuarkNutritionTooltipMixin {
    @ModifyVariable(method = "makeTooltip", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private static boolean cuisinejournal$hideFoodIcons(boolean showFoodIcons) {
        return false;
    }
}
