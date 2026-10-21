package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.FluidProductionTracker;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets={"umpaz.brewinandchewin.common.block.entity.KegBlockEntity",
        "dev.xkmc.youkaishomecoming.content.pot.kettle.KettleBlockEntity",
        "dev.xkmc.youkaishomecoming.content.pot.ferment.FermentationTankBlockEntity",
        "dev.xkmc.youkaishomecoming.content.pot.basin.BasinBlockEntity"},remap=false)
public abstract class FluidStationOwnershipMixin {
    @Inject(method="getCapability",at=@At("HEAD"),remap=false)
    private void cuisinejournal$bind(CallbackInfoReturnable<?> cir){FluidProductionTracker.tank((BlockEntity)(Object)this);}
}
