package com.chi__you.cuisinejournal.mixin.unification;

import com.chi__you.cuisinejournal.compat.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class UnifiedCropPlantingMixin {
    @Inject(method="useOn",at=@At("HEAD"),cancellable=true)
    private void cuisinejournal$planting(UseOnContext context,CallbackInfoReturnable<InteractionResult> cir){
        if(context.getLevel().isClientSide)return;
        var stack=(ItemStack)(Object)this;
        var peanut=UnificationCrops.plantPeanut(context,stack);
        if(peanut!=null){cir.setReturnValue(peanut);return;}
        if(UnificationCrops.blocksPlanting(BuiltInRegistries.ITEM.getKey(stack.getItem()),
                UnificationRuntime.plan(context.getLevel().getServer())))cir.setReturnValue(InteractionResult.FAIL);
    }
}
