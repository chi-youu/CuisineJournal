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
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.BambooTrayBlockEntity",remap=false)
public abstract class KaleidoscopeTrayMixin {
    @Inject(method="serverTick",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$begin(Level level,BlockPos pos,BlockState state,@Coerce Object tray,CallbackInfo ci){
        KaleidoscopeSpeed.beforeArray((BlockEntity)tray,"processingProgress");KaleidoscopeProduction.begin((BlockEntity)tray,null);
    }
    @Inject(method="serverTick",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$end(Level level,BlockPos pos,BlockState state,@Coerce Object tray,CallbackInfo ci){
        KaleidoscopeSpeed.afterArray((BlockEntity)tray,"processingProgress");KaleidoscopeProduction.end((BlockEntity)tray);
    }
}
