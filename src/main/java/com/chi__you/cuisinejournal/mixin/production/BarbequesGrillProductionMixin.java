package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.compat.HostReflection;
import com.chi__you.cuisinejournal.tracking.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo
@Mixin(targets="com.mao.barbequesdelight.content.block.GrillBlockEntity",remap=false)
public abstract class BarbequesGrillProductionMixin {
    @Unique private ItemStack[] cuisinejournal$before;
    @Inject(method="addItem",at=@At("RETURN"),remap=false)
    private void cuisinejournal$insert(int slot,ItemStack stack,CallbackInfoReturnable<Boolean> cir){if(cir.getReturnValue())ManualGrillProduction.bbqInserted((BlockEntity)(Object)this,slot);}
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfo ci){
        cuisinejournal$before=new ItemStack[2];
        for(int i=0;i<2;i++){Object raw=HostReflection.invoke(this,"getStack",i);cuisinejournal$before[i]=raw instanceof ItemStack item?item.copy():ItemStack.EMPTY;}
    }
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfo ci){
        if(cuisinejournal$before==null)return;
        Object entries=HostReflection.field(this,"entries");
        if(entries instanceof Object[] values)for(int i=0;i<Math.min(2,values.length);i++){
            Object raw=HostReflection.invoke(this,"getStack",i);var before=cuisinejournal$before[i];
            if(raw instanceof ItemStack after&&!before.isEmpty()&&!after.isEmpty()&&!ItemStack.isSameItem(before,after)
                    &&Boolean.TRUE.equals(HostReflection.field(values[i],"flipped"))&&!Boolean.TRUE.equals(HostReflection.field(values[i],"burnt")))
                ManualGrillProduction.output((BlockEntity)(Object)this,before,after,"barbequesdelight:grilling");
        }
        cuisinejournal$before=null;
    }
}
