package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class CulinaryOutputSlotMixin {
    @Inject(method="clicked",at=@At("HEAD"))
    private void cuisinejournal$begin(int index,int button,ClickType type,Player player,CallbackInfo ci) {
        ProductionTracker.beginMenu(player,(AbstractContainerMenu)(Object)this);
        ProductionTracker.prepareClick(player,(AbstractContainerMenu)(Object)this,index,button,type);
    }
    @Inject(method="clicked",at=@At("RETURN"))
    private void cuisinejournal$end(int index,int button,ClickType type,Player player,CallbackInfo ci) {
        ProductionTracker.endClick();
        ProductionTracker.endOperation(player);
    }
    @Inject(method="doClick",at=@At("HEAD"),cancellable=true)
    private void cuisinejournal$swap(int index,int button,ClickType type,Player player,CallbackInfo ci) {
        if(type==ClickType.SWAP&&ProductionTracker.swapOutput(player,(AbstractContainerMenu)(Object)this,index,button))ci.cancel();
    }
    @Inject(method="canTakeItemForPickAll",at=@At("HEAD"),cancellable=true)
    private void cuisinejournal$noBlindMerge(ItemStack cursor,Slot slot,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if(ProductionTracker.isProductionResultSlot(slot))cir.setReturnValue(false);
    }
    @Redirect(method="doClick",at=@At(value="INVOKE",target="Lnet/minecraft/world/inventory/AbstractContainerMenu;quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack cuisinejournal$quickMove(AbstractContainerMenu menu,Player player,int index) {
        if(!(player instanceof ServerPlayer)||index<0||index>=menu.slots.size())return menu.quickMoveStack(player,index);
        Slot slot=menu.slots.get(index);
        if(!slot.mayPickup(player))return menu.quickMoveStack(player,index);
        ItemStack result=ProductionTracker.quickMoveOutput((ServerPlayer)player,menu,slot);
        return result==null?menu.quickMoveStack(player,index):result;
    }
}
