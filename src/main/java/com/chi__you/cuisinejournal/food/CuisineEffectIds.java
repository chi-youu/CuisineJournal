package com.chi__you.cuisinejournal.food;

import net.minecraft.resources.ResourceLocation;

/** Saved mature references work with either provider without rewriting the preset. */
public final class CuisineEffectIds {
    public static final ResourceLocation ICE = ResourceLocation.parse("cuisinejournal:ice_ice_ice");
    public static final ResourceLocation MATURE = ResourceLocation.parse("cuisinejournal:mature");
    public static final ResourceLocation YOUKAI_MATURE = ResourceLocation.parse("youkaishomecoming:mature");
    private static volatile boolean youkaiLoaded;

    private CuisineEffectIds() {}
    public static void configure(boolean loaded) { youkaiLoaded = loaded; }
    public static ResourceLocation resolve(ResourceLocation id) { return resolve(id, youkaiLoaded); }
    public static ResourceLocation resolve(ResourceLocation id, boolean loaded) {
        return MATURE.equals(id) || YOUKAI_MATURE.equals(id) ? loaded ? YOUKAI_MATURE : MATURE : id;
    }
}
