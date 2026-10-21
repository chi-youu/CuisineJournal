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
@Mixin(targets={"com.bmt.kaleidoscope_chinesefood.block.entity.PickleJarBlockEntity",
        "com.bmt.kaleidoscope_world_liquor.block.entity.FreezerBlockEntity"},remap=false)
public abstract class KaleidoscopeFermentMixin {
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$begin(Level level,BlockPos pos,BlockState state,@Coerce Object be,CallbackInfo ci){
        KaleidoscopeSpeed.beforeProgress((BlockEntity)be);KaleidoscopeProduction.begin((BlockEntity)be,null);
    }
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$end(Level level,BlockPos pos,BlockState state,@Coerce Object be,CallbackInfo ci){
        KaleidoscopeSpeed.afterProgress((BlockEntity)be);KaleidoscopeProduction.end((BlockEntity)be);
    }
}
