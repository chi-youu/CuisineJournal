package com.chi__you.foodinteractions;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.List;

/** Reads the host's eight mix-to-bucket mappings rather than guessing item names. */
public final class TrailIceCreamRelations {
    public static final String EMPTY = "show.tatd.mod.block.ice_cream.IceBucketBlock";
    public static final String FROZEN = "show.tatd.mod.block.ice_cream.IceCreamBucketBlock";
    public record Relation(int type, ResourceLocation mix, Block frozen, ResourceLocation serving, int servings) {}
    private TrailIceCreamRelations() {}

    public static List<Relation> read(Block bucket) throws ReflectiveOperationException {
        if (!bucket.getClass().getName().equals(EMPTY)) return List.of();
        var mixMethod = bucket.getClass().getDeclaredMethod("getIceCreamMixItem", int.class);
        var blockMethod = bucket.getClass().getDeclaredMethod("getIceCreamBucketBlock", int.class);
        mixMethod.setAccessible(true); blockMethod.setAccessible(true);
        List<Relation> result = new ArrayList<>();
        for (int type = 1; type <= 8; type++) {
            Item mix = (Item) mixMethod.invoke(bucket, type);
            Block frozen = (Block) blockMethod.invoke(bucket, type);
            ItemStack serving = (ItemStack) frozen.getClass().getMethod("getServingItem",
                    net.minecraft.world.level.block.state.BlockState.class).invoke(frozen, frozen.defaultBlockState());
            int servings = (Integer) frozen.getClass().getMethod("getMaxServings").invoke(frozen);
            if (!serving.isEmpty() && servings > 0) result.add(new Relation(type,
                    BuiltInRegistries.ITEM.getKey(mix), frozen, BuiltInRegistries.ITEM.getKey(serving.getItem()), servings));
        }
        return List.copyOf(result);
    }
}
