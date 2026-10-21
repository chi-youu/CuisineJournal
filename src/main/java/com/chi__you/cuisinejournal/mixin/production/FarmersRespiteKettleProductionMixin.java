package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.FluidProductionTracker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="umpaz.farmersrespite.common.block.entity.KettleBlockEntity",remap=false)
public abstract class FarmersRespiteKettleProductionMixin {
    @Unique private net.minecraftforge.fluids.FluidStack cuisinejournal$before;
    @Inject(method="processBrewing",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfoReturnable<Boolean> cir){FluidProductionTracker.begin((BlockEntity)(Object)this,true);}
    @Inject(method="processBrewing",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfoReturnable<Boolean> cir){FluidProductionTracker.end((BlockEntity)(Object)this);}
    @Inject(method="fluidExtract",at=@At("HEAD"),remap=false)
    private void cuisinejournal$pourBegin(CallbackInfoReturnable<ItemStack> cir){
        var be=(BlockEntity)(Object)this;if(be.getLevel()!=null&&!be.getLevel().isClientSide)
            cuisinejournal$before=FluidProductionTracker.tank(be).getFluidInTank(0).copy();
    }
    @Inject(method="fluidExtract",at=@At("RETURN"),remap=false)
    private void cuisinejournal$pourEnd(CallbackInfoReturnable<ItemStack> cir){
        var be=(BlockEntity)(Object)this;if(cuisinejournal$before==null)return;
        var batch=FluidProductionTracker.bottle(be,cuisinejournal$before,cir.getReturnValue(),false);cuisinejournal$before=null;
        if(batch!=null)FluidProductionTracker.recordBottled(be,4,batch);
    }
}
