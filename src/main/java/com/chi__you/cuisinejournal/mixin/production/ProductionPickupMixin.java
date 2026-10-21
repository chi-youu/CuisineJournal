package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(ItemEntity.class)
public abstract class ProductionPickupMixin {
    @Redirect(method="playerTouch",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean cuisinejournal$claim(Inventory inventory,ItemStack stack){return ProductionTracker.pickup(inventory,stack);}
}
