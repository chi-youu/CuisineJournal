package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.CuisineEffectAdapters;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEffect.class)
public abstract class SaturationCombatMixin {
    @Inject(method="applyEffectTick", at=@At("HEAD"), cancellable=true)
    private void cuisinejournal$saturation(LivingEntity entity, int amplifier, CallbackInfo ci) {
        if ((Object)this == MobEffects.SATURATION && CuisineEffectAdapters.applySaturation(entity)) ci.cancel();
    }
}
