package com.chi__you.cuisinejournal.mixin.client;

import com.chi__you.cuisinejournal.client.hud.VanillaNutritionHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="xy177.twilightsparksdelight.integration.appleskin.TSDAppleSkinCompat",remap=false)
public abstract class TwilightAppleSkinOverflowMixin {
    @Inject(method="afterOverlay",at=@At("HEAD"),cancellable=true,remap=false)
    private static void cuisinejournal$overflow(CallbackInfo ci) {
        if(VanillaNutritionHud.replacesTwilightOverlay())ci.cancel();
    }
}
