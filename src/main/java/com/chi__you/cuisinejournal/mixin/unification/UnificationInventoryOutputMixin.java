package com.chi__you.cuisinejournal.mixin.unification;

import com.chi__you.cuisinejournal.compat.UnificationRuntime;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class UnificationInventoryOutputMixin {
    @ModifyVariable(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), argsOnly = true)
    private ItemStack cuisinejournal$output(ItemStack stack) { return UnificationRuntime.inventoryOutput(stack); }
    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"))
    private void cuisinejournal$remainder(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        UnificationRuntime.inventoryDone();
    }
}
