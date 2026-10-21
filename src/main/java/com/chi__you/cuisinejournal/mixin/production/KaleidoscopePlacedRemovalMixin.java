package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.PlacedFoodVariants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class KaleidoscopePlacedRemovalMixin {
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void cuisinejournal$forget(BlockPos pos,BlockState state,boolean moving,CallbackInfoReturnable<BlockState> cir){
        BlockState old=cir.getReturnValue();
        if(old!=null&&!old.isAir()&&old.getBlock()!=state.getBlock()
                &&((LevelChunk)(Object)this).getLevel() instanceof net.minecraft.server.level.ServerLevel server)
            com.chi__you.cuisinejournal.tracking.PlacedDataCleanup.changed(server,pos);
        if(old!=null&&old.getBlock()!=state.getBlock()
                &&net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(old.getBlock()).getNamespace().startsWith("kaleidoscope_"))
            PlacedFoodVariants.clear(((LevelChunk)(Object)this).getLevel(),pos);
    }
}
