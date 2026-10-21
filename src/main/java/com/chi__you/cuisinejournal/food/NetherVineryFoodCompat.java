package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostClasses;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import java.util.*;

/** NetherVinery short native actions retain their original trigger timing. */
public final class NetherVineryFoodCompat {
    private NetherVineryFoodCompat() {}

    public static boolean delayed(ItemStack stack){return HostClasses.matches(stack.getItem(),"net.satisfy.nethervinery.core.block.NetherDrinkBlockItem")
            &&Set.of("nether_fizz","lava_fizz","improved_nether_fizz","improved_lava_fizz").contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());}

    public static boolean delayedNative(){return FoodUseContext.active()&&delayed(FoodUseContext.current());}
}

