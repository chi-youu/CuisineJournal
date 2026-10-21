package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="com.aetherteam.aether.blockentity.AbstractAetherFurnaceBlockEntity",remap=false)
public abstract class AetherAltarProductionMixin {
    @Inject(method="m_266209_",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfoReturnable<Boolean> cir){ProductionTracker.beginCooking((BlockEntity)(Object)this);}
    @Inject(method="m_266209_",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfoReturnable<Boolean> cir){ProductionTracker.endCooking((BlockEntity)(Object)this);}
}
