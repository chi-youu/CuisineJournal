package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="net.mcreator.simplecream.procedures.IceCreamsRecipesProcedure",remap=false)
public abstract class SimpleCreamProductionMixin {
    @Inject(method="execute",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$begin(LevelAccessor level,double x,double y,double z,CallbackInfo ci){ProductionTracker.beginCooking(level.getBlockEntity(BlockPos.containing(x,y,z)));}
    @Inject(method="execute",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$end(LevelAccessor level,double x,double y,double z,CallbackInfo ci){ProductionTracker.endCooking(level.getBlockEntity(BlockPos.containing(x,y,z)));}
}
