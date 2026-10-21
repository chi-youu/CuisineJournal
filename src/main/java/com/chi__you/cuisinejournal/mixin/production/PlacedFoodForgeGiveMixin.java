package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.food.PlacedFoodConsumption;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Procedure-based servings use Forge's helper instead of Inventory.add. */
@Mixin(value=ItemHandlerHelper.class,remap=false)
public abstract class PlacedFoodForgeGiveMixin {
    @Inject(method="giveItemToPlayer(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$stamp(Player player,ItemStack stack,int slot,CallbackInfo ci){
        PlacedFoodConsumption.inventoryOutput(stack);com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction.claim(player,stack);
    }
    @Redirect(method="giveItemToPlayer(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V",
            at=@At(value="INVOKE",target="Lnet/minecraftforge/items/IItemHandler;insertItem(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"),remap=false)
    private static ItemStack cuisinejournal$slot(IItemHandler handler,int slot,ItemStack stack,boolean simulate){
        ItemStack copy=stack.copy();var remainder=handler.insertItem(slot,stack,simulate);
        if(!simulate)PlacedFoodConsumption.inventoryOutputDone(copy,copy.getCount()-remainder.getCount());return remainder;
    }
    @Redirect(method="giveItemToPlayer(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V",
            at=@At(value="INVOKE",target="Lnet/minecraftforge/items/ItemHandlerHelper;insertItemStacked(Lnet/minecraftforge/items/IItemHandler;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"),remap=false)
    private static ItemStack cuisinejournal$stacked(IItemHandler handler,ItemStack stack,boolean simulate){
        ItemStack copy=stack.copy();var remainder=ItemHandlerHelper.insertItemStacked(handler,stack,simulate);
        if(!simulate)PlacedFoodConsumption.inventoryOutputDone(copy,copy.getCount()-remainder.getCount());return remainder;
    }
}
