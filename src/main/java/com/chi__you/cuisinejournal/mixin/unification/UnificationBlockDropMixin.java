package com.chi__you.cuisinejournal.mixin.unification;

import com.chi__you.cuisinejournal.compat.UnificationRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Block.class)
public abstract class UnificationBlockDropMixin {
    @ModifyVariable(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), argsOnly = true)
    private static ItemStack cuisinejournal$harvest(ItemStack stack, Level level, BlockPos pos, ItemStack original) {
        return UnificationRuntime.produced(level, stack);
    }
}
