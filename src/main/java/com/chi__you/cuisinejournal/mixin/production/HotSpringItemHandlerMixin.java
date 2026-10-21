package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.HotSpringSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(value={ItemStackHandler.class,net.minecraftforge.items.wrapper.InvWrapper.class},remap=false)
public abstract class HotSpringItemHandlerMixin {
    @Unique private final Deque<ItemStack> cuisinejournal$sources=new ArrayDeque<>();
    @Inject(method="insertItem",at=@At("HEAD"),remap=false)
    private void cuisinejournal$clean(int slot,ItemStack item,boolean simulate,CallbackInfoReturnable<ItemStack> cir){cuisinejournal$sources.push(HotSpringSource.marked(item)?item.copy():ItemStack.EMPTY);HotSpringSource.clean(item);}
    @Inject(method="insertItem",at=@At("RETURN"),remap=false)
    private void cuisinejournal$restore(int slot,ItemStack item,boolean simulate,CallbackInfoReturnable<ItemStack> cir){ItemStack source=cuisinejournal$sources.pop();HotSpringSource.restoreSource(cir.getReturnValue(),source);HotSpringSource.restoreSource(item,source);}
}
