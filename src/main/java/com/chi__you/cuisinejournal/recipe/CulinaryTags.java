package com.chi__you.cuisinejournal.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Tag identifiers Cuisine Journal consults, declared by id rather than by
 * referencing anyone's code.
 *
 * <p>Every tag here is optional. A tag that no installed mod populates simply
 * matches nothing. Conventional namespaces are considered explicitly for this
 * Forge 1.20.1 distribution; equal tag paths do not imply equal members.</p>
 */
public final class CulinaryTags {
    private CulinaryTags() {
    }

    public static TagKey<Item> item(String namespace, String path) {
        return TagKey.create(Registries.ITEM, new ResourceLocation(namespace, path));
    }

    public static TagKey<Block> block(String namespace, String path) {
        return TagKey.create(Registries.BLOCK, new ResourceLocation(namespace, path));
    }

    /** Farmer's Delight feast blocks remain serving workstations. */
    public static final TagKey<Block> FEAST_BLOCKS = block("farmersdelight", "feasts");

    /**
     * Blocks treated as cooking workstations.
     *
     * <p>This classifies workstation blocks; actual production attribution
     * still requires a supported adapter and verified slots or host hooks.</p>
     *
     * <p>Mods that reuse Farmer's Delight's blocks need no entry at all: the
     * generic recipe detection already found their dishes.</p>
     */
    public static final TagKey<Block> WORKSTATION_BLOCKS = block("cuisinejournal", "workstations");
}
