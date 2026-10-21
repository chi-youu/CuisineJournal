package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="dev.xkmc.youkaishomecoming.content.pot.base.BasePotBlockEntity",remap=false)
public abstract class YoukaiMokaProductionMixin {
    @Inject(method="processCooking",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfoReturnable<Boolean> cir){ProductionTracker.beginCooking((BlockEntity)(Object)this);}
    @Inject(method="processCooking",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfoReturnable<Boolean> cir){ProductionTracker.endCooking((BlockEntity)(Object)this);}
    @Inject(method={"moveMealToOutput","useStoredContainersOnMeal"},at=@At("HEAD"),remap=false)
    private void cuisinejournal$serveBegin(CallbackInfo ci){ProductionTracker.beginTransfer((BlockEntity)(Object)this);}
    @Inject(method={"moveMealToOutput","useStoredContainersOnMeal"},at=@At("RETURN"),remap=false)
    private void cuisinejournal$serveEnd(CallbackInfo ci){ProductionTracker.endTransfer((BlockEntity)(Object)this);}
    @Inject(method="useHeldItemOnMeal",at=@At("HEAD"),remap=false)
    private void cuisinejournal$heldBegin(ItemStack container,CallbackInfoReturnable<ItemStack> cir){ProductionTracker.beginHeldMeal((BlockEntity)(Object)this,container);}
    @Inject(method="useHeldItemOnMeal",at=@At("RETURN"),remap=false)
    private void cuisinejournal$heldEnd(ItemStack container,CallbackInfoReturnable<ItemStack> cir){ProductionTracker.heldMeal((BlockEntity)(Object)this,container,cir.getReturnValue());}
}
