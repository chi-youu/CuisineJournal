package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.LetsDoDelivery;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

@Mixin(Inventory.class)
public abstract class LetsDoDeliveryMixin {
    @Unique private final Deque<Optional<LetsDoDelivery.Frame>> cuisinejournal$deliveries=new ArrayDeque<>();
    @Inject(method="add(ILnet/minecraft/world/item/ItemStack;)Z",at=@At("HEAD"))
    private void cuisinejournal$before(int slot,ItemStack stack,CallbackInfoReturnable<Boolean> cir){
        cuisinejournal$deliveries.push(Optional.ofNullable(LetsDoDelivery.begin((Inventory)(Object)this,stack)));
    }
    @Inject(method="add(ILnet/minecraft/world/item/ItemStack;)Z",at=@At("RETURN"))
    private void cuisinejournal$after(int slot,ItemStack stack,CallbackInfoReturnable<Boolean> cir){
        if(!cuisinejournal$deliveries.isEmpty())LetsDoDelivery.end((Inventory)(Object)this,stack,cuisinejournal$deliveries.pop().orElse(null));
    }
}
