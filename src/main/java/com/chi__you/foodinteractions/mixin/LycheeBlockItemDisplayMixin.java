package com.chi__you.foodinteractions.mixin;

import com.chi__you.foodinteractions.FoodInteractionScanner;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Custom shield-like food items place blocks without using BlockItem's reverse map. */
@Pseudo
@Mixin(targets="snownee.lychee.core.def.BlockPredicateHelper",remap=false)
public abstract class LycheeBlockItemDisplayMixin {
    @Inject(method="getMatchedItemStacks",at=@At("RETURN"),cancellable=true,remap=false)
    private static void foodinteractions$customPlaceables(BlockPredicate predicate,CallbackInfoReturnable<List<ItemStack>> cir) {
        try {
            Object raw=Class.forName("snownee.lychee.core.def.BlockPredicateHelper")
                    .getMethod("getMatchedBlocks",BlockPredicate.class).invoke(null,predicate);
            if(!(raw instanceof java.util.Set<?> blocks))return;
            List<ItemStack> items=new ArrayList<>(cir.getReturnValue());
            for(Object value:blocks)if(value instanceof Block block) {
                var source=FoodInteractionScanner.source(block);
                if(source!=null&&items.stream().noneMatch(stack->BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(source)))
                    items.add(new ItemStack(BuiltInRegistries.ITEM.get(source)));
            }
            cir.setReturnValue(List.copyOf(items));
        }catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){}
    }
}
