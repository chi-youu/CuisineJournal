package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.doggystudio.chirencqr.ltc.server.block.entity.LatiaoOvenBlockEntity",remap=false)
public abstract class LatiaoOvenProductionMixin {
    @Inject(method="serverTick",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$begin(Level level,BlockPos pos,BlockState state,@Coerce BlockEntity be,CallbackInfo ci){ProductionTracker.beginCooking(be);}
    @Inject(method="serverTick",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$end(Level level,BlockPos pos,BlockState state,@Coerce BlockEntity be,CallbackInfo ci){ProductionTracker.endCooking(be);}
}
