package com.chi__you.cuisinejournal.mixin.food;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface CookingUseTimer {
    @Accessor("useItemRemaining") void cuisinejournal$remaining(int ticks);
}
