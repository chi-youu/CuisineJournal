package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.tracking.LetsDoSpeed;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="net.satisfy.camping.core.world.block.entity.GrillBlockEntity",remap=false)
public abstract class LetsDoGrillSpeedMixin {
    @Inject(method="cookTick",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$before(Level level,BlockPos pos,BlockState state,@Coerce Object be,CallbackInfo ci){LetsDoSpeed.beforeArray((BlockEntity)be,"cookingProgress");}
    @Inject(method="cookTick",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$after(Level level,BlockPos pos,BlockState state,@Coerce Object be,CallbackInfo ci){LetsDoSpeed.afterArray((BlockEntity)be,"cookingProgress");}
}
