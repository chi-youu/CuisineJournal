package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets={"com.github.ysbbbbbb.kaleidoscopecookery.util.ItemUtils",
        "com.github.ysbbbbbb.kaleidoscopetavern.util.ItemUtils"},remap=false)
public abstract class KaleidoscopeGiveMixin {
    @Inject(method="getItemToLivingEntity(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;I)V",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$claim(LivingEntity entity,ItemStack stack,int slot,CallbackInfo ci){
        com.chi__you.cuisinejournal.tracking.KaleidoscopeServings.output(stack);KaleidoscopeProduction.claim(entity,stack);
    }
}
