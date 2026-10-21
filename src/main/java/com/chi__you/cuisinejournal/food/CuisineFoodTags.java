package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.CuisineJournal;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;

public final class CuisineFoodTags {
    public static final TagKey<Item> TWO_SLOT = TagKey.create(Registries.ITEM,
            new ResourceLocation(CuisineJournal.MOD_ID, "two_slot_foods"));
    public static final TagKey<Item> THREE_SLOT = TagKey.create(Registries.ITEM,
            new ResourceLocation(CuisineJournal.MOD_ID, "three_slot_foods"));

    private CuisineFoodTags() {
    }
}
