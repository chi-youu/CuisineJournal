package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.bmt.kaleidoscope_chinesefood.block.entity.FreezerBlockEntity",remap=false)
public abstract class KaleidoscopeFreezerMixin {
    @Inject(method="serverTick",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$begin(Level level,BlockPos pos,BlockState state,@Coerce Object be,CallbackInfo ci){
        KaleidoscopeSpeed.beforeArray((BlockEntity)be,"progress");KaleidoscopeProduction.begin((BlockEntity)be,null);
    }
    @Inject(method="serverTick",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$end(Level level,BlockPos pos,BlockState state,@Coerce Object be,CallbackInfo ci){
        KaleidoscopeSpeed.afterArray((BlockEntity)be,"progress");KaleidoscopeProduction.end((BlockEntity)be);
    }
}
