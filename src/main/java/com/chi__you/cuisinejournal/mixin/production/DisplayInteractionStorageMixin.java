package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.DisplayFoodStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.jkvin114.displaydelight.events.InterationManager",remap=false)
public abstract class DisplayInteractionStorageMixin {
    @Inject(method={"tryPlaceItemOnPlate","tryPlaceItemOnSmallPlate"},at=@At("HEAD"),remap=false)
    private static void cuisinejournal$beginInsert(Player player,ServerLevel level,BlockHitResult hit,boolean mainHand,CallbackInfoReturnable<Boolean> cir){
        DisplayFoodStorage.beginInsert(player,level,hit.getBlockPos(),mainHand);
    }
    @Inject(method="tryPlaceItem",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$beginWorldPlace(Player player,ServerLevel level,BlockHitResult hit,boolean mainHand,CallbackInfoReturnable<Boolean> cir){
        DisplayFoodStorage.beginWorldPlace(player,level,hit,mainHand);
    }
    @Inject(method={"tryPlaceItemOnPlate","tryPlaceItemOnSmallPlate","tryPlaceItem"},at=@At("RETURN"),remap=false)
    private static void cuisinejournal$endInsert(CallbackInfoReturnable<Boolean> cir){DisplayFoodStorage.endInsert(cir.getReturnValue());}
    @Inject(method="tryTakeItemWithBareHand",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$beginTake(Player player,ServerLevel level,BlockHitResult hit,CallbackInfoReturnable<Boolean> cir){
        DisplayFoodStorage.beginTake(level,hit.getBlockPos());
    }
    @Inject(method="tryTakeItemWithBareHand",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$endTake(CallbackInfoReturnable<Boolean> cir){DisplayFoodStorage.endTake(cir.getReturnValue());}
    @Redirect(method="tryTakeItemWithBareHand",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Inventory;m_36054_(Lnet/minecraft/world/item/ItemStack;)Z"),remap=false)
    private static boolean cuisinejournal$inventory(Inventory inventory,ItemStack stack){return DisplayFoodStorage.intoInventory(inventory.player,stack);}
    @Redirect(method="tryTakeItemWithBareHand",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;m_21008_(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V"),remap=false)
    private static void cuisinejournal$hand(Player player,InteractionHand hand,ItemStack stack){DisplayFoodStorage.intoHand(player,hand,stack);}
}
