package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.IceBucketPours;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="show.tatd.mod.block.ice_cream.IceBucketBlock",remap=false)
public abstract class TrailIceBucketMixin {
    @Inject(method="m_213898_",at=@At("RETURN"),remap=false)
    private void cuisinejournal$frozen(BlockState before, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        IceBucketPours.frozen(level,pos,before);
    }
}
