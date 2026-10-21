package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.CuisineFoodRuntime;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="umpaz.brewinandchewin.common.effect.SweetHeartEffect", remap=false)
public abstract class BrewinWarmHeartMixin {
    @Inject(method={"m_6742_", "applyEffectTick"}, at=@At("HEAD"), cancellable=true, remap=false)
    private void cuisinejournal$warmHeart(LivingEntity entity, int amplifier, CallbackInfo ci) {
        if (CuisineFoodRuntime.activeForCurrentThread()) ci.cancel();
    }
}
