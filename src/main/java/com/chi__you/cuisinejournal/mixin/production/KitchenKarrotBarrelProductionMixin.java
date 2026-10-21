package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="io.github.tt432.kitchenkarrot.blockentity.BrewingBarrelBlockEntity",remap=false)
public abstract class KitchenKarrotBarrelProductionMixin {
    @Inject(method="finishBrewing",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfo ci){ProductionTracker.beginCooking((net.minecraft.world.level.block.entity.BlockEntity)(Object)this);}
    @Inject(method="finishBrewing",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfo ci){ProductionTracker.endCooking((net.minecraft.world.level.block.entity.BlockEntity)(Object)this);}
}
