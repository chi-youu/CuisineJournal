package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.food.PlacedFoodConsumption;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class PlacedFoodInventoryOutputMixin {
    @org.spongepowered.asm.mixin.Unique private int cuisinejournal$beforeCount;
    @org.spongepowered.asm.mixin.Unique private ItemStack cuisinejournal$original;
    @org.spongepowered.asm.mixin.Unique private com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction.InventoryDelivery cuisinejournal$delivery;
    @org.spongepowered.asm.mixin.injection.Inject(method="add(Lnet/minecraft/world/item/ItemStack;)Z",at=@At("HEAD"))
    private void cuisinejournal$inherit(ItemStack stack,CallbackInfoReturnable<Boolean> cir) {
        cuisinejournal$beforeCount=stack.getCount();
        cuisinejournal$original=stack.copy();
        PlacedFoodConsumption.inventoryOutput(stack);
        cuisinejournal$delivery=com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction.inventoryBegin(((Inventory)(Object)this).player,stack);
    }
    @org.spongepowered.asm.mixin.injection.Inject(method="add(Lnet/minecraft/world/item/ItemStack;)Z",at=@At("RETURN"))
    private void cuisinejournal$finish(ItemStack stack,CallbackInfoReturnable<Boolean> cir) {
        com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction.inventoryEnd(((Inventory)(Object)this).player,stack,cuisinejournal$delivery);
        cuisinejournal$delivery=null;
        PlacedFoodConsumption.inventoryOutputDone(cuisinejournal$original,cuisinejournal$beforeCount-stack.getCount());
        cuisinejournal$original=ItemStack.EMPTY;
    }
    @Redirect(method="placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Inventory;add(ILnet/minecraft/world/item/ItemStack;)Z"))
    private boolean cuisinejournal$returnPortion(Inventory inventory,int slot,ItemStack stack){
        int before=stack.getCount();ItemStack copy=stack.copy();PlacedFoodConsumption.inventoryOutput(stack);
        boolean added=inventory.add(slot,stack);PlacedFoodConsumption.inventoryOutputDone(copy,before-stack.getCount());return added;
    }
}
