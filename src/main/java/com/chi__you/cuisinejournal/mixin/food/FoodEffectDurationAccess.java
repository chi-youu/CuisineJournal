package com.chi__you.cuisinejournal.mixin.food;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MobEffectInstance.class)
public interface FoodEffectDurationAccess {
    @Accessor("duration") void cuisinejournal$duration(int duration);
}
