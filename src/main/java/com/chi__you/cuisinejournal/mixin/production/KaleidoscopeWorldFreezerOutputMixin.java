package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.bmt.kaleidoscope_world_liquor.block.entity.FreezerBlockEntity$3",remap=false)
public abstract class KaleidoscopeWorldFreezerOutputMixin {
    @Inject(method="getStackInSlot",at=@At("RETURN"),remap=false)
    private void cuisinejournal$variant(CallbackInfoReturnable<ItemStack> cir){
        Object owner=HostReflection.field(this,"this$0");
        if(owner instanceof BlockEntity be)KaleidoscopeProduction.output(be,cir.getReturnValue());
    }
    @Inject(method="extractItem",at=@At("RETURN"),remap=false)
    private void cuisinejournal$extract(int slot,int amount,boolean simulate,CallbackInfoReturnable<ItemStack> cir){
        Object owner=HostReflection.field(this,"this$0");
        if(owner instanceof BlockEntity be)KaleidoscopeProduction.output(be,cir.getReturnValue(),!simulate);
    }
}
