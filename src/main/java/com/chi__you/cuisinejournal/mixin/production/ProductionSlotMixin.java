package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Mixin(Slot.class)
public abstract class ProductionSlotMixin {
    @Inject(method="onTake",at=@At("HEAD"))
    private void cuisinejournal$hostTake(Player player,ItemStack stack,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction.claim(player,stack);
    }
    @Unique private ProductionTracker.Extraction cuisinejournal$take;
    @Inject(method="tryRemove",at=@At("HEAD"))
    private void cuisinejournal$prepare(int amount,int limit,Player player,CallbackInfoReturnable<Optional<ItemStack>> cir) {
        cuisinejournal$take=ProductionTracker.prepare(player,player.containerMenu,(Slot)(Object)this,
                player.containerMenu.getCarried().isEmpty(),-1);
    }
    @Inject(method="tryRemove",at=@At("RETURN"))
    private void cuisinejournal$finish(int amount,int limit,Player player,CallbackInfoReturnable<Optional<ItemStack>> cir) {
        var tx=cuisinejournal$take;cuisinejournal$take=null;
        ProductionTracker.finish(tx,cir.getReturnValue().map(ItemStack::getCount).orElse(0));
        cir.getReturnValue().ifPresent(result->ProductionTracker.markExtracted(tx,result));
    }
}
