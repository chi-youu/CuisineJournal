package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.compat.HostReflection;
import com.chi__you.cuisinejournal.tracking.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="cn.breezeth.kaleidoscope_grilling.grill.GrillBlockEntity",remap=false)
public abstract class KaleidoscopeGrillProductionMixin {
    @Unique private java.util.Set<ItemStack> cuisinejournal$existing;
    @Unique private boolean cuisinejournal$simulatedExtraction;
    @Inject(method="extractOneStack",at=@At("HEAD"),remap=false)
    private void cuisinejournal$preview(boolean simulate,CallbackInfoReturnable<ItemStack> cir){
        cuisinejournal$simulatedExtraction=simulate;
    }
    @Inject(method="extractOneStack",at=@At("RETURN"),remap=false)
    private void cuisinejournal$previewEnded(boolean simulate,CallbackInfoReturnable<ItemStack> cir){
        cuisinejournal$simulatedExtraction=false;
    }
    @Inject(method="insert(Lnet/minecraft/world/item/ItemStack;Z)Z",at=@At("HEAD"),remap=false)
    private void cuisinejournal$insert(ItemStack stack,boolean consume,CallbackInfoReturnable<Boolean> cir){
        cuisinejournal$existing=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Object items=HostReflection.field(this,"items");
        if(items instanceof java.util.List<?> list)for(Object raw:list)if(raw instanceof ItemStack item&&!item.isEmpty())cuisinejournal$existing.add(item);
    }
    @Inject(method="insert(Lnet/minecraft/world/item/ItemStack;Z)Z",at=@At("RETURN"),remap=false)
    private void cuisinejournal$inserted(CallbackInfoReturnable<Boolean> cir){
        Object items=HostReflection.field(this,"items");
        if(cir.getReturnValue()&&cuisinejournal$existing!=null&&items instanceof java.util.List<?> list)
            for(Object raw:list)if(raw instanceof ItemStack item&&!item.isEmpty()&&!cuisinejournal$existing.contains(item))
                ManualGrillProduction.inserted(item,ProductionTracker.actor());
        cuisinejournal$existing=null;
    }
    @Inject(method="cookedOutput",at=@At("RETURN"),remap=false)
    private void cuisinejournal$output(ItemStack input,CallbackInfoReturnable<ItemStack> cir){
        Object phase=HostReflection.field(this,"phase"),seasoned=HostReflection.field(this,"seasoned");
        if(!cuisinejournal$simulatedExtraction&&phase instanceof Number n&&n.intValue()==2&&Boolean.TRUE.equals(seasoned)
                &&!Boolean.TRUE.equals(HostReflection.field(this,"failed")))
            ManualGrillProduction.output((BlockEntity)(Object)this,input,cir.getReturnValue(),"kaleidoscope_grilling:grilling");
    }
}
