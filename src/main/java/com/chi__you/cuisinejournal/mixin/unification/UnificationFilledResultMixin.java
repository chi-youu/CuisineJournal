package com.chi__you.cuisinejournal.mixin.unification;

import com.chi__you.cuisinejournal.compat.UnificationRuntime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ItemUtils.class)
public abstract class UnificationFilledResultMixin {
    @ModifyVariable(method = "createFilledResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static ItemStack cuisinejournal$filled(ItemStack output, ItemStack empty, Player player, ItemStack originalOutput, boolean creative) {
        return UnificationRuntime.produced(player.level(), output);
    }
}
