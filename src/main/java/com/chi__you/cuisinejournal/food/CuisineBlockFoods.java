package com.chi__you.cuisinejournal.food;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;

/** Resolves placed cake and pie blocks to the item actually consumed per bite. */
public final class CuisineBlockFoods {
    private CuisineBlockFoods() {
    }

    public static ItemStack cake() {
        if (ModList.get().isLoaded("farmersdelight")) {
            Item item = BuiltInRegistries.ITEM.getOptional(
                    new net.minecraft.resources.ResourceLocation("farmersdelight", "cake_slice"))
                    .orElse(null);
            if (item != null) {
                return new ItemStack(item);
            }
        }
        return Items.CAKE.getDefaultInstance();
    }

    public static ItemStack copy(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
    }
}
