package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.HotSpringSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class HotSpringItemMergeMixin {
    @Inject(method="merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V",at=@At("HEAD"))
    private static void cuisinejournal$merge(ItemEntity first,net.minecraft.world.item.ItemStack into,ItemEntity second,net.minecraft.world.item.ItemStack from,CallbackInfo ci) {
        if(!first.level().isClientSide)HotSpringSource.mixed(first,second);
    }
}
