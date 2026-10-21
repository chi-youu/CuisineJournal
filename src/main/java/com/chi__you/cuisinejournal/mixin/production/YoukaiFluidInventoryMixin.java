package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.FluidProductionTracker;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The host fluid helper is an interface; observe its inventory delivery through a class target. */
@Mixin(Inventory.class)
public abstract class YoukaiFluidInventoryMixin {
    @Shadow @Final public net.minecraft.world.entity.player.Player player;

    @Inject(method="placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;)V",at=@At("HEAD"))
    private void cuisinejournal$claim(ItemStack result,CallbackInfo ci) {
        var owner=FluidProductionTracker.currentOwner();
        if(owner==null||!owner.getClass().getName().startsWith("dev.xkmc.youkaishomecoming.")
                ||ProductionTracker.actor()!=player)return;
        FluidProductionTracker.claimReturned(result);
    }
}
