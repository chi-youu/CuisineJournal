package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.FluidProductionTracker;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="dev.xkmc.youkaishomecoming.content.pot.basin.BasinBlockEntity",remap=false)
public abstract class YoukaiBasinProductionMixin {
    @Inject(method="process",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfo ci){FluidProductionTracker.begin((BlockEntity)(Object)this,true);}
    @Inject(method="process",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfo ci){FluidProductionTracker.end((BlockEntity)(Object)this);}
}
