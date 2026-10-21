package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.CuisineFoodEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class FoodEffectLifecycleMixin {
    @Inject(method="removeEffectNoUpdate",at=@At("RETURN"))
    private void cuisinejournal$removedDirectly(MobEffect effect,CallbackInfoReturnable<MobEffectInstance> cir) {
        CuisineFoodEffects.removed((LivingEntity)(Object)this,cir.getReturnValue());
    }
    @Inject(method="onEffectRemoved",at=@At("HEAD"))
    private void cuisinejournal$removed(MobEffectInstance effect,CallbackInfo ci) {
        CuisineFoodEffects.removed((LivingEntity)(Object)this,effect);
    }
    @Inject(method="curePotionEffects",at=@At("HEAD"),remap=false)
    private void cuisinejournal$cureBegin(ItemStack cure,CallbackInfoReturnable<Boolean> cir) {
        CuisineFoodEffects.beginCure((LivingEntity)(Object)this);
    }
    @Inject(method="curePotionEffects",at=@At("RETURN"),remap=false)
    private void cuisinejournal$cureEnd(ItemStack cure,CallbackInfoReturnable<Boolean> cir) {
        CuisineFoodEffects.endCure((LivingEntity)(Object)this);
    }
}
