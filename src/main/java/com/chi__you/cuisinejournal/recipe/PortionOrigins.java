package com.chi__you.cuisinejournal.recipe;

import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;

/** Only unambiguous portions without an independent cooking recipe share a preparation identity. */
public final class PortionOrigins {
    private PortionOrigins() {}

    public static Map<ResourceLocation,ResourceLocation> resolve(Map<ResourceLocation,Set<ResourceLocation>> candidates,
                                                                Set<ResourceLocation> independentlyCooked) {
        return resolve(candidates, independentlyCooked, Map.of());
    }

    public static Map<ResourceLocation,ResourceLocation> resolve(Map<ResourceLocation,Set<ResourceLocation>> candidates,
                                                                Set<ResourceLocation> independentlyCooked,
                                                                Map<ResourceLocation,ResourceLocation> configured) {
        Map<ResourceLocation,Set<ResourceLocation>> effective = new LinkedHashMap<>(candidates);
        configured.forEach((portion, parent) -> effective.put(portion, Set.of(parent)));
        Map<ResourceLocation,ResourceLocation> direct=new LinkedHashMap<>();
        effective.forEach((portion,sources)->{
            if(!independentlyCooked.contains(portion)&&sources.size()==1) {
                ResourceLocation source=sources.iterator().next();
                if(!portion.equals(source))direct.put(portion,source);
            }
        });
        Map<ResourceLocation,ResourceLocation> result=new LinkedHashMap<>();
        direct.forEach((portion,source)->{
            Set<ResourceLocation> seen=new LinkedHashSet<>();seen.add(portion);
            ResourceLocation current=source;
            while(direct.containsKey(current)&&seen.add(current))current=direct.get(current);
            if(!seen.contains(current))result.put(portion,current);
        });
        return Map.copyOf(result);
    }

    /** Assembling only this whole's own portions is repacking, not another preparation. */
    public static List<RecipeEntry> preparations(ResourceLocation whole, List<RecipeEntry> recipes,
                                                Map<ResourceLocation,ResourceLocation> origins) {
        Set<ResourceLocation> portions = new LinkedHashSet<>();
        origins.forEach((portion, parent) -> { if (parent.equals(whole)) portions.add(portion); });
        return recipes.stream().filter(recipe -> {
            Set<ResourceLocation> inputs = new LinkedHashSet<>();
            recipe.ingredients().forEach(slot -> inputs.addAll(slot.items()));
            if (inputs.isEmpty()) return true;
            if (portions.containsAll(inputs)) return false;
            // A bowl or bottle does not turn assembling the same servings into cooking.
            boolean packing = recipe.method().equals(CookingMethod.CRAFTING)
                    && recipe.ingredients().stream().allMatch(slot -> !slot.items().isEmpty()
                        && slot.items().stream().allMatch(item -> portions.contains(item) || container(item)))
                    && recipe.ingredients().stream().anyMatch(slot -> !slot.items().isEmpty()
                        && portions.containsAll(slot.items()));
            return !packing;
        }).toList();
    }

    /** Raw food returned from a plate is not a newly prepared slice of that plate. */
    public static boolean packedServing(ResourceLocation whole, ResourceLocation serving, List<RecipeEntry> recipes) {
        return !recipes.isEmpty() && preparations(whole, recipes, Map.of(serving, whole)).isEmpty();
    }

    private static boolean container(ResourceLocation item) {
        return Set.of("minecraft:bowl", "minecraft:glass_bottle", "minecraft:bucket").contains(item.toString());
    }

    /** Only portions with a genuine parent recipe enter the catalogue through this path. */
    public static Map<ResourceLocation,ResourceLocation> admit(Map<ResourceLocation,List<RecipeEntry>> catalogue,
                                                              Map<ResourceLocation,List<RecipeEntry>> parentRecipes,
                                                              Map<ResourceLocation,ResourceLocation> origins) {
        Map<ResourceLocation,ResourceLocation> admitted = new LinkedHashMap<>();
        origins.forEach((portion, parent) -> {
            if (!parentRecipes.getOrDefault(parent, List.of()).isEmpty()) {
                catalogue.computeIfAbsent(portion, unused -> new java.util.ArrayList<>());
                admitted.put(portion, parent);
            }
        });
        return Map.copyOf(admitted);
    }
}
