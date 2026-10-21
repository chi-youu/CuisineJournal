package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.compat.HostReflection;
import com.chi__you.cuisinejournal.tracking.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo
@Mixin(targets="com.kazi_cat.papercraft_magic_decoration.blockentity.CopperStillBlockEntity",remap=false)
public abstract class PapercraftStillProductionMixin {
    @Inject(method="addFuel",at=@At("RETURN"),remap=false)
    private void cuisinejournal$started(CallbackInfoReturnable<Boolean> cir){
        if(cir.getReturnValue()){Object raw=HostReflection.field(this,"result");if(raw instanceof ItemStack result)PapercraftProduction.produce((BlockEntity)(Object)this,result);}
    }
    @Inject(method="takeOutResult",at=@At("HEAD"),remap=false)
    private void cuisinejournal$ready(CallbackInfoReturnable<Boolean> cir){
        if(Integer.valueOf(3).equals(HostReflection.invoke(this,"getStatus"))){
            Object raw=HostReflection.field(this,"result");if(raw instanceof ItemStack result)PapercraftProduction.settled((BlockEntity)(Object)this,result);}
    }
}
