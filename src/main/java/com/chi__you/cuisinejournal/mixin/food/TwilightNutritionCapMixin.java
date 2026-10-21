package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.CuisineFoodRuntime;
import com.chi__you.cuisinejournal.skills.VanillaSkillNutrition;
import com.chi__you.cuisinejournal.skills.TwilightNutritionCompat;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the host's progress calculation and every clamp/HUD caller on the same cap. */
@Pseudo
@Mixin(targets="xy177.twilightsparksdelight.common.food.ExtendedFoodProgression",remap=false)
public abstract class TwilightNutritionCapMixin {
    @Inject(method="getMaxFood",at=@At("RETURN"),cancellable=true,remap=false)
    private static void cuisinejournal$foodCap(Player player,CallbackInfoReturnable<Integer> result) {
        if(player!=null&&!TwilightNutritionCompat.querying()&&!CuisineFoodRuntime.activeForCurrentThread())
            result.setReturnValue(VanillaSkillNutrition.higherCap(result.getReturnValue(),VanillaSkillNutrition.ownHungerCap(player)));
    }

    @Inject(method="getMaxSaturation",at=@At("RETURN"),cancellable=true,remap=false)
    private static void cuisinejournal$saturationCap(Player player,CallbackInfoReturnable<Float> result) {
        if(player!=null&&!TwilightNutritionCompat.querying()&&!CuisineFoodRuntime.activeForCurrentThread())
            result.setReturnValue((float)VanillaSkillNutrition.higherCap(TwilightNutritionCompat.cap(player,true),VanillaSkillNutrition.ownSaturationCap(player)));
    }
}
