package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.HotSpringSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(Inventory.class)
public abstract class HotSpringInventoryMixin {
    @Unique private final Deque<ItemStack> cuisinejournal$sources=new ArrayDeque<>();
    @Inject(method="add(ILnet/minecraft/world/item/ItemStack;)Z",at=@At("HEAD"))
    private void cuisinejournal$clean(int slot,ItemStack item,CallbackInfoReturnable<Boolean> cir){cuisinejournal$sources.push(HotSpringSource.marked(item)?item.copy():ItemStack.EMPTY);HotSpringSource.clean(item);}
    @Inject(method="add(ILnet/minecraft/world/item/ItemStack;)Z",at=@At("RETURN"))
    private void cuisinejournal$restore(int slot,ItemStack item,CallbackInfoReturnable<Boolean> cir){HotSpringSource.restoreSource(item,cuisinejournal$sources.pop());}
}
