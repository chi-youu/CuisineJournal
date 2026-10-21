package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.FluidProductionTracker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
@Pseudo
@Mixin(targets="umpaz.brewinandchewin.common.block.entity.KegBlockEntity",remap=false)
public abstract class KegProductionMixin {
    // Only the first capacity read sizes the drain-to-container branch; filling still uses capacity.
    @Redirect(method="fluidExtract",at=@At(value="INVOKE",target="Lnet/minecraftforge/fluids/capability/templates/FluidTank;getCapacity()I",ordinal=0,remap=false),remap=false)
    private int cuisinejournal$availableForPouring(FluidTank tank){return tank.getFluidAmount();}

    @Inject(method="processFermenting",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfoReturnable<Boolean> cir){FluidProductionTracker.begin((BlockEntity)(Object)this,true);}
    @Inject(method="processFermenting",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfoReturnable<Boolean> cir){FluidProductionTracker.end((BlockEntity)(Object)this);}
    @Inject(method="fluidExtract",at=@At("HEAD"),remap=false)
    private void cuisinejournal$servingBegin(ItemStack container,int max,boolean gui,boolean creative,CallbackInfoReturnable<List<ItemStack>> cir){FluidProductionTracker.beginKegServing((BlockEntity)(Object)this,container,gui,creative);}
    @Inject(method="fluidExtract",at=@At("RETURN"),remap=false)
    private void cuisinejournal$servingEnd(ItemStack container,int max,boolean gui,boolean creative,CallbackInfoReturnable<List<ItemStack>> cir){FluidProductionTracker.endKegServing((BlockEntity)(Object)this,cir.getReturnValue());}
}
