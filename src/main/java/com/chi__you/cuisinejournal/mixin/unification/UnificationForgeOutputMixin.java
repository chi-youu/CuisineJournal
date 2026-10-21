package com.chi__you.cuisinejournal.mixin.unification;

import com.chi__you.cuisinejournal.compat.UnificationRuntime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = ItemHandlerHelper.class, remap = false)
public abstract class UnificationForgeOutputMixin {
    @ModifyVariable(method = "giveItemToPlayer(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V",
            at = @At("HEAD"), argsOnly = true, remap = false)
    private static ItemStack cuisinejournal$output(ItemStack stack) { return UnificationRuntime.interactionOutput(stack); }
}
