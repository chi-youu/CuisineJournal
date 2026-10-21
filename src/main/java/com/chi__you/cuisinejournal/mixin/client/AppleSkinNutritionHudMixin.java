package com.chi__you.cuisinejournal.mixin.client;

import com.chi__you.cuisinejournal.food.CuisineGameplayRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="squeek.appleskin.client.HUDOverlayHandler", remap=false)
public abstract class AppleSkinNutritionHudMixin {
    @Inject(method="shouldRenderAnyOverlays", at=@At("HEAD"), cancellable=true, remap=false)
    private static void cuisinejournal$nutrition(CallbackInfoReturnable<Boolean> cir) {
        if(!CuisineGameplayRules.nutritionDisplayEnabled())cir.setReturnValue(false);
    }
    @Inject(method="renderExhaustion", at=@At("HEAD"), cancellable=true, remap=false)
    private static void cuisinejournal$exhaustion(ForgeGui gui, GuiGraphics graphics, float partialTick,
                                                int width, int height, CallbackInfo ci) {
        if(!CuisineGameplayRules.nutritionDisplayEnabled())ci.cancel();
    }
}
