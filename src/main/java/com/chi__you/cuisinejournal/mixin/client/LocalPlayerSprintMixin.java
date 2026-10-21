package com.chi__you.cuisinejournal.mixin.client;

import com.chi__you.cuisinejournal.food.CuisineFoodClientState;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSprintMixin {
    @Inject(method = "hasEnoughFoodToStartSprinting", at = @At("HEAD"), cancellable = true)
    private void cuisinejournal$requireFoodToSprint(CallbackInfoReturnable<Boolean> cir) {
        if (!CuisineFoodClientState.active()) {
            return;
        }
        LocalPlayer player = (LocalPlayer) (Object) this;
        cir.setReturnValue(player.getAbilities().mayfly
                || CuisineFoodClientState.snapshot().foods().stream()
                        .anyMatch(slot -> CuisineFoodClientState.displayedRemaining(slot) > 0));
    }
}
