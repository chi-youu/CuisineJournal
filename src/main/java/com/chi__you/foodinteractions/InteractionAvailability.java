package com.chi__you.foodinteractions;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import java.util.function.Predicate;

/** Shared pack contract, also usable by the standalone viewer without loading Cuisine Journal. */
public final class InteractionAvailability {
    public static final ResourceLocation DISABLED_ITEMS = ResourceLocation.parse("cuisinejournal:disabled_items");
    private static final TagKey<Item> DISABLED = TagKey.create(Registries.ITEM, DISABLED_ITEMS);
    private InteractionAvailability() {}

    public static boolean disabled(ResourceLocation item) {
        return item != null && BuiltInRegistries.ITEM.containsKey(item)
                && BuiltInRegistries.ITEM.get(item).builtInRegistryHolder().is(DISABLED);
    }

    public static boolean visible(InteractionDisplay entry, Predicate<ResourceLocation> disabled) {
        return !disabled.test(entry.output()) && (entry.source() == null || !disabled.test(entry.source()));
    }
}
