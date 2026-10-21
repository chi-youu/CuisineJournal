package com.chi__you.cuisinejournal.mixin.food;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Random milk cleansing follows the same reserve policy as a standard curative item. */
@Pseudo
@Mixin(targets="com.teamabnormals.neapolitan.common.item.MilkBottleItem",remap=false)
public abstract class NeapolitanMilkCureMixin {
    @Inject(method="clearRandomEffect",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$begin(Level level,LivingEntity entity,CallbackInfo ci){com.chi__you.cuisinejournal.food.CuisineFoodEffects.beginCure(entity);}
    @Inject(method="clearRandomEffect",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$end(Level level,LivingEntity entity,CallbackInfo ci){com.chi__you.cuisinejournal.food.CuisineFoodEffects.endCure(entity);}
}
