package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets={"vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity","vectorwing.farmersdelight.common.block.entity.AbstractStoveBlockEntity"},remap=false)
public abstract class ProductionFdCookingMixin {
    @Inject(method="cookAndOutputItems",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfo ci){ProductionTracker.beginCooking((BlockEntity)(Object)this);}
    @Inject(method="cookAndOutputItems",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfo ci){ProductionTracker.endCooking((BlockEntity)(Object)this);}
}
