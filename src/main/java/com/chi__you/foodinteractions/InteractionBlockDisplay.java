package com.chi__you.foodinteractions;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** Share the same block-to-item identity between recipe indexing, preview clicks and names. */
public final class InteractionBlockDisplay {
    private InteractionBlockDisplay() {}

    public static Item sourceItem(Block block) {
        var source=FoodInteractionScanner.source(block);
        return source!=null&&BuiltInRegistries.ITEM.containsKey(source)
                ? BuiltInRegistries.ITEM.get(source) : block.asItem();
    }

    public static MutableComponent name(Block block) {
        if(Language.getInstance().has(block.getDescriptionId()))return block.getName();
        Item item=sourceItem(block);
        return item==Items.AIR ? block.getName() : new ItemStack(item).getHoverName().copy();
    }
}
