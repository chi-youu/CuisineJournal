package com.chi__you.cuisinejournal.client.journal;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;
import net.minecraftforge.common.CreativeModeTabRegistry;

/** Item order from real creative-tab contents, grouped by item namespace. */
public final class CreativeFoodOrder {
    private Map<ResourceLocation, Integer> positions = Map.of();
    private boolean initialized;

    /** Called when the catalogue refreshes, never while rendering individual cells. */
    public void refresh() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        boolean rebuilt = CreativeModeTabs.tryRebuildTabContents(minecraft.level.enabledFeatures(),
                minecraft.player.canUseGameMasterBlocks(), minecraft.level.registryAccess());
        if (initialized && !rebuilt) return;
        List<List<ResourceLocation>> tabs = CreativeModeTabRegistry.getSortedCreativeModeTabs().stream()
                .filter(tab -> tab.getType() == CreativeModeTab.Type.CATEGORY)
                .map(tab -> tab.getDisplayItems().stream()
                        .filter(stack -> !stack.isEmpty())
                        .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem())).toList())
                .toList();
        positions = firstPositions(tabs);
        initialized = true;
    }

    public Comparator<ResourceLocation> comparator() {
        return comparator(positions);
    }

    public Comparator<ResourceLocation> comparator(List<String> preferredMods) {
        return comparator(positions, preferredMods);
    }

    static Map<ResourceLocation, Integer> firstPositions(List<List<ResourceLocation>> tabs) {
        Map<ResourceLocation, Integer> positions = new HashMap<>();
        int position = 0;
        for (List<ResourceLocation> tab : tabs) {
            for (ResourceLocation item : tab) {
                positions.putIfAbsent(item, position++);
            }
        }
        return Map.copyOf(positions);
    }

    static Comparator<ResourceLocation> comparator(Map<ResourceLocation, Integer> positions) {
        Map<String, Integer> mods = new HashMap<>();
        positions.forEach((item, position) -> mods.merge(item.getNamespace(), position, Math::min));
        return Comparator.<ResourceLocation>comparingInt(item -> mods.getOrDefault(item.getNamespace(), Integer.MAX_VALUE))
                .thenComparing(ResourceLocation::getNamespace)
                .thenComparingInt(item -> positions.getOrDefault(item, Integer.MAX_VALUE))
                .thenComparing(ResourceLocation::toString);
    }

    public static Comparator<ResourceLocation> preparedComparator(ToLongFunction<ResourceLocation> prepared,
                                                                  Comparator<ResourceLocation> creative) {
        return Comparator.comparingLong(prepared).reversed().thenComparing(creative);
    }

    static Comparator<ResourceLocation> comparator(Map<ResourceLocation, Integer> positions,
                                                   List<String> preferredMods) {
        List<String> priority = List.copyOf(preferredMods);
        return Comparator.<ResourceLocation>comparingInt(item -> {
            int index = priority.indexOf(item.getNamespace());
            return index < 0 ? priority.size() : index;
        }).thenComparing(comparator(positions));
    }
}
