package com.chi__you.cuisinejournal.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;

import java.util.ArrayList;
import java.util.List;

/** Derives the stable, server-authoritative ingredient catalogue from dishes. */
public final class IngredientCatalogue {
    public static final ResourceLocation FRUIT = id("fruit");
    public static final ResourceLocation VEGETABLE = id("vegetable");
    public static final ResourceLocation MEAT = id("meat");
    public static final ResourceLocation EGG = id("egg");
    public static final ResourceLocation DAIRY = id("dairy");
    public static final ResourceLocation GRAIN = id("grain");
    public static final ResourceLocation SEASONING = id("seasoning");
    public static final ResourceLocation OTHER = id("other");

    private IngredientCatalogue() {}

    public static boolean isUsableIngredient(ResourceLocation id) {
        if (IngredientCatalogueConfig.current().excluded(id)) return false;
        if (!BuiltInRegistries.ITEM.containsKey(id)) return false;
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null) return false;
        if (!HostItemAvailability.enabled(item)) return false;
        ItemStack stack = new ItemStack(item);
        if (stack.getMaxDamage() > 0 || item instanceof TieredItem || item instanceof BucketItem) return false;
        String name = item.getClass().getName();
        return !name.contains("Tool") && !name.contains("Knife") && !name.contains("Utensil");
    }

    public static List<ResourceLocation> types(ResourceLocation id) {
        List<ResourceLocation> override = IngredientCatalogueConfig.current().typeOverride(id);
        if (!override.isEmpty()) return override;
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        List<ResourceLocation> result = new ArrayList<>();
        addIfTag(result, stack, FRUIT, "fruits", "fruit", "berries");
        addIfTag(result, stack, VEGETABLE, "vegetables", "vegetable", "salad_ingredients");
        addIfTag(result, stack, MEAT, "raw_meat", "raw_meats", "cooked_meats", "raw_fishes", "cooked_fishes");
        addIfTag(result, stack, EGG, "eggs", "egg");
        addIfTag(result, stack, DAIRY, "milk", "milks", "dairy");
        addIfTag(result, stack, GRAIN, "grain", "grains", "bread", "dough", "pasta");
        addIfTag(result, stack, SEASONING, "spices", "seasonings", "salt");
        return result.isEmpty() ? List.of(OTHER) : List.copyOf(result);
    }

    private static void addIfTag(List<ResourceLocation> result, ItemStack stack, ResourceLocation type,
                                 String... paths) {
        for (String path : paths) {
            if (stack.is(TagKey.create(Registries.ITEM, id("c", path)))
                    || stack.is(TagKey.create(Registries.ITEM, id("forge", path)))) {
                result.add(type);
                return;
            }
        }
    }

    public static byte kind(ResourceLocation id, CulinaryRegistry registry) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (registry.entry(id) != null) return 0;
        return item != null && com.chi__you.cuisinejournal.food.CuisineEdibility.isEdible(new ItemStack(item))
                ? (byte) 1 : (byte) 2;
    }

    public static IngredientCatalogueConfig.Flavor flavor(ResourceLocation id) {
        IngredientCatalogueConfig.Flavor value = IngredientCatalogueConfig.current().flavor(id);
        return value != null && value.source() ? value : null;
    }

    /** A tag entry's own flavor (configured on any of its merged tag ids). */
    public static IngredientCatalogueConfig.Flavor tagFlavor(List<ResourceLocation> tagIds) {
        IngredientCatalogueConfig.Flavor value = IngredientCatalogueConfig.current().tagFlavor(tagIds);
        return value != null && value.source() ? value : null;
    }

    /** The most common type among a tag's members, so a tag lands under one type chip. */
    public static List<ResourceLocation> tagTypes(List<ResourceLocation> members) {
        java.util.Map<ResourceLocation, Integer> counts = new java.util.LinkedHashMap<>();
        for (ResourceLocation member : members) {
            for (ResourceLocation type : types(member)) counts.merge(type, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(OTHER))
                .max(java.util.Map.Entry.comparingByValue())
                .map(entry -> List.of(entry.getKey())).orElse(List.of(OTHER));
    }

    public static List<ResourceLocation> tagTypes(List<ResourceLocation> members,
                                                   List<ResourceLocation> tagIds) {
        List<ResourceLocation> override = IngredientCatalogueConfig.current().tagTypeOverride(tagIds);
        if (!override.isEmpty()) return override;
        return tagTypes(members);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cuisinejournal", path);
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
