package com.chi__you.cuisinejournal.mixin.client;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="squeek.appleskin.client.TooltipOverlayHandler", remap=false)
public abstract class AppleSkinNutritionTooltipMixin {
    @Inject(method="shouldShowTooltip", at=@At("HEAD"), cancellable=true, remap=false)
    private static void cuisinejournal$nutrition(ItemStack stack, Player player, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
