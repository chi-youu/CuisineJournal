package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.IceBucketPours;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.class)
public abstract class IceBucketRemovalMixin {
    @Inject(method="onRemove",at=@At("HEAD"))
    private void cuisinejournal$remove(BlockState before, Level level, BlockPos pos, BlockState after, boolean moving, CallbackInfo ci) {
        IceBucketPours.removed(level,pos,before,after);
    }
}
