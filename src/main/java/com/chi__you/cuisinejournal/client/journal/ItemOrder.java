package com.chi__you.cuisinejournal.client.journal;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every item in the order JEI shows by default: Minecraft first, then each mod by display name,
 * and inside a mod the creative-menu order (items missing from every tab go last, by id).
 */
public final class ItemOrder {
    private static List<ResourceLocation> cached = List.of();
    private static int cachedSize = -1;

    private ItemOrder() {
    }

    public static synchronized List<ResourceLocation> all() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean rebuilt = minecraft.level != null && minecraft.player != null
                && CreativeModeTabs.tryRebuildTabContents(minecraft.level.enabledFeatures(),
                minecraft.player.canUseGameMasterBlocks(), minecraft.level.registryAccess());
        int size = BuiltInRegistries.ITEM.size();
        if (!rebuilt && size == cachedSize && !cached.isEmpty()) return cached;
        Map<ResourceLocation, Integer> positions = new HashMap<>();
        int position = 0;
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
            for (var stack : tab.getDisplayItems()) {
                if (stack.isEmpty()) continue;
                positions.putIfAbsent(BuiltInRegistries.ITEM.getKey(stack.getItem()), position++);
            }
        }
        Map<String, String> modNames = new HashMap<>();
        List<ResourceLocation> items = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (BuiltInRegistries.ITEM.get(id) == Items.AIR) continue;
            items.add(id);
            modNames.computeIfAbsent(id.getNamespace(), namespace ->
                    JournalText.modName(namespace).getString().toLowerCase(Locale.ROOT));
        }
        items.sort(Comparator.<ResourceLocation>comparingInt(id -> id.getNamespace().equals("minecraft") ? 0 : 1)
                .thenComparing(id -> modNames.get(id.getNamespace()))
                .thenComparing(ResourceLocation::getNamespace)
                .thenComparingInt(id -> positions.getOrDefault(id, Integer.MAX_VALUE))
                .thenComparing(ResourceLocation::toString));
        cached = List.copyOf(items);
        cachedSize = size;
        return cached;
    }
}
