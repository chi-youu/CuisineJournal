package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.CuisineAquaEffectWindow;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class AquaLivingEntityMixin {
    @ModifyVariable(method = {"addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z",
            "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"},
            at = @At("HEAD"), argsOnly = true)
    private MobEffectInstance cuisinejournal$limitTemporaryEffect(MobEffectInstance effect) {
        return com.chi__you.cuisinejournal.food.CuisineEffectAdapters.limitNativeEffect((LivingEntity)(Object)this,effect);
    }
    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z",
            at = @At("HEAD"), cancellable = true)
    private void cuisinejournal$blockAquaEffect(MobEffectInstance effect,
            CallbackInfoReturnable<Boolean> cir) {
        if (CuisineAquaEffectWindow.blocks((LivingEntity) (Object) this)
                ||com.chi__you.cuisinejournal.food.CuisineEffectAdapters.blocksDigestedNative((LivingEntity)(Object)this)
                ||com.chi__you.cuisinejournal.food.PlacedFoodConsumption.blocksNativeEffect((LivingEntity)(Object)this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void cuisinejournal$blockAquaEffectWithSource(MobEffectInstance effect, Entity source,
            CallbackInfoReturnable<Boolean> cir) {
        if (CuisineAquaEffectWindow.blocks((LivingEntity) (Object) this)
                ||com.chi__you.cuisinejournal.food.CuisineEffectAdapters.blocksDigestedNative((LivingEntity)(Object)this)
                ||com.chi__you.cuisinejournal.food.PlacedFoodConsumption.blocksNativeEffect((LivingEntity)(Object)this)) {
            cir.setReturnValue(false);
        }
    }
}
