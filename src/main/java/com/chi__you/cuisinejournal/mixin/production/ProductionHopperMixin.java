package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.core.Direction;
@Mixin(HopperBlockEntity.class)
public abstract class ProductionHopperMixin {
    @Redirect(method="addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
            at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/entity/HopperBlockEntity;addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack cuisinejournal$unowned(Container from,Container to,ItemStack stack,Direction side){
        ItemStack original=stack.copy();ProductionTracker.machinePickup(stack);
        ItemStack remainder=HopperBlockEntity.addItem(from,to,stack,side);
        if(!remainder.isEmpty()&&original.hasTag()&&original.getTag().contains(ProductionTracker.PENDING))
            remainder.setTag(original.getTag().copy());
        com.chi__you.cuisinejournal.tracking.HotSpringSource.restoreSource(remainder,original);
        return remainder;
    }
}
