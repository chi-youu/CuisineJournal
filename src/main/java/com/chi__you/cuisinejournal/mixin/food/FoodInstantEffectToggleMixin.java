package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.CuisineEffectAdapters;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEffect.class)
public abstract class FoodInstantEffectToggleMixin {
    @Inject(method="applyInstantenousEffect", at=@At("HEAD"), cancellable=true)
    private void cuisinejournal$foodOnly(Entity source, Entity indirect, LivingEntity target,
                                       int amplifier, double strength, CallbackInfo ci) {
        if(CuisineEffectAdapters.blocksFoodEffects(target))ci.cancel();
    }
}
