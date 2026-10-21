package com.chi__you.cuisinejournal.mixin.client;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreativeModeTabs.class)
public interface UnificationCreativeTabsAccessor {
    @Accessor("CACHED_PARAMETERS")
    static void cuisinejournal$invalidate(CreativeModeTab.ItemDisplayParameters parameters) {
        throw new AssertionError();
    }
}
