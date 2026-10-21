package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class ProductionFurnaceMixin {
    @Inject(method="burn",at=@At("HEAD"))
    private void cuisinejournal$begin(CallbackInfoReturnable<Boolean> cir){ProductionTracker.beginCooking((AbstractFurnaceBlockEntity)(Object)this);}
    @Inject(method="burn",at=@At("RETURN"))
    private void cuisinejournal$end(CallbackInfoReturnable<Boolean> cir){ProductionTracker.endCooking((AbstractFurnaceBlockEntity)(Object)this);}
}
