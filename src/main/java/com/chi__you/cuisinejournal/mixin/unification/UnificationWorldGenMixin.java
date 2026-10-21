package com.chi__you.cuisinejournal.mixin.unification;

import com.chi__you.cuisinejournal.compat.UnificationRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
public abstract class UnificationWorldGenMixin {
    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void cuisinejournal$wild(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
        var region = (WorldGenRegion) (Object) this;
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.BushBlock)) return;
        var block = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        var plan = UnificationRuntime.plan(region.getServer());
        if (plan.disabledPlants().contains(block.toString())) cir.setReturnValue(false);
    }
}
