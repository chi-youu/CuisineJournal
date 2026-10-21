package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.FluidProductionTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="dev.xkmc.l2library.base.tile.BaseTank",remap=false)
public abstract class YoukaiTankProductionMixin {
    @Inject(method={"fill","drain"},at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfoReturnable<?> cir){FluidProductionTracker.beginTank(this);}
    @Inject(method={"fill","drain"},at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfoReturnable<?> cir){FluidProductionTracker.endTank(this);}
}
