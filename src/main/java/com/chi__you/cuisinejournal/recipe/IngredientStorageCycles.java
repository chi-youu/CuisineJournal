package com.chi__you.cuisinejournal.recipe;

import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Hide storage-only reverse ingredients when every known production route packs the same food. */
public final class IngredientStorageCycles {
    public record Production(boolean crafting, int count, List<RecipeEntry.IngredientSlot> inputs) {
        public Production { inputs = List.copyOf(inputs); }
    }
    private IngredientStorageCycles() {}
    public static Set<ResourceLocation> hidden(Map<ResourceLocation,List<Production>> recipes,
                                              List<IngredientJournalIndex.Input> uses, Set<ResourceLocation> unreadable) {
        Map<ResourceLocation,Set<ResourceLocation>> dishes = new HashMap<>();
        for (var use : uses) for (var item : use.slot().items())
            dishes.computeIfAbsent(item, unused -> new HashSet<>()).add(use.dish());
        Set<ResourceLocation> result = new HashSet<>();
        for (var entry : recipes.entrySet()) {
            var stored = entry.getKey();
            if (unreadable.contains(stored) || entry.getValue().isEmpty()) continue;
            ResourceLocation food = null;
            boolean packing = true;
            for (var recipe : entry.getValue()) {
                if (!recipe.crafting() || recipe.count() != 1 || recipe.inputs().size() < 2) { packing = false; break; }
                for (var slot : recipe.inputs()) {
                    if (!slot.tags().isEmpty() || slot.explicitItems().size() != 1) { packing = false; break; }
                    var input = slot.explicitItems().get(0);
                    if (food == null) food = input;
                    else if (!food.equals(input)) { packing = false; break; }
                }
                if (!packing) break;
            }
            if (!packing || food == null || stored.equals(food) || !dishes.getOrDefault(stored, Set.of()).equals(Set.of(food))) continue;
            boolean unpacking = recipes.getOrDefault(food, List.of()).stream().anyMatch(recipe -> recipe.crafting()
                    && recipe.count() > 1 && recipe.inputs().stream().anyMatch(slot -> slot.explicitItems().contains(stored))
                    && recipe.inputs().stream().allMatch(slot -> slot.tags().isEmpty() && slot.explicitItems().size() == 1
                        && (slot.explicitItems().get(0).equals(stored) || container(slot.explicitItems().get(0)))));
            if (unpacking) result.add(stored);
        }
        return Set.copyOf(result);
    }
    private static boolean container(ResourceLocation item) {
        return Set.of("minecraft:glass_bottle", "minecraft:bowl", "minecraft:bucket").contains(item.toString());
    }
}
