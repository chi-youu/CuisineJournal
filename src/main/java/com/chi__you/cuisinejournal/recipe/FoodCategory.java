package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.Constants;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A dish or ingredient category derived from tags rather than from a hardcoded
 * item list.
 *
 * <p>Definitions are ordered: the first matching definition wins, so a feast
 * is reported as a feast rather than as a meal even though Farmer's Delight
 * puts several items in both tags. Datapacks may prepend their own definitions
 * to override the built-ins entirely.</p>
 *
 * @param id       stable identifier used in configuration and datapacks
 * @param tags     any of these tags matching classifies an item into this category
 * @param priority lower sorts first; datapack definitions default below built-ins
 */
public record FoodCategory(ResourceLocation id, List<TagKey<Item>> tags, int priority,
                           Set<ResourceLocation> items, ResourceLocation icon,
                           List<Subcategory> subcategories, String titleKey,
                           boolean literalTitle) {

    public static final ResourceLocation UNCATEGORISED_ID =
            new ResourceLocation(Constants.MOD_ID, "uncategorised");

    /** Returned when no definition matches; never null, never guessed. */
    public static final FoodCategory UNCATEGORISED =
            new FoodCategory(UNCATEGORISED_ID, List.of(), Integer.MAX_VALUE, Set.of(), null, List.of(), null, false);

    public static final ResourceLocation OTHER_FOOD_ID =
            new ResourceLocation(Constants.MOD_ID, "other_food");

    /**
     * Fallback for an edible dish no specific definition matched.
     *
     * <p>Most of what a real server detects is like this: vanilla foods and an
     * addon's raw produce carry none of Farmer's Delight's dish tags. Filing
     * them as "Other Food" rather than "Uncategorised" is honest — they really
     * are food — and it keeps the audit's uncategorised count meaningful as a
     * "these matched nothing specific" diagnostic.</p>
     */
    public static final FoodCategory OTHER_FOOD =
            new FoodCategory(OTHER_FOOD_ID, List.of(), Integer.MAX_VALUE - 1, Set.of(), null, List.of(), null, false);

    /** Compatibility constructor for existing callers and tests. */
    public FoodCategory(ResourceLocation id, List<TagKey<Item>> tags, int priority,
                        Set<ResourceLocation> items, ResourceLocation icon) {
        this(id, tags, priority, items, icon, List.of(), null, false);
    }

    public FoodCategory {
        tags = List.copyOf(tags);
        items = Set.copyOf(items);
        subcategories = List.copyOf(subcategories);
    }

    /** A second-level grouping inside one top-level category. */
    public record Subcategory(ResourceLocation id, String titleKey, boolean literalTitle,
                              ResourceLocation icon, List<TagKey<Item>> tags,
                              Set<ResourceLocation> items, boolean drinkFallback) {
        public Subcategory(ResourceLocation id, String titleKey, boolean literalTitle,
                           ResourceLocation icon, List<TagKey<Item>> tags, Set<ResourceLocation> items) {
            this(id, titleKey, literalTitle, icon, tags, items, false);
        }

        public Subcategory {
            tags = List.copyOf(tags);
            items = Set.copyOf(items);
        }

        boolean matches(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            if (items.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                return true;
            }
            for (TagKey<Item> tag : tags) {
                if (stack.is(tag)) {
                    return true;
                }
            }
            return false;
        }

        boolean matchesFallback(ItemStack stack) {
            return drinkFallback && com.chi__you.cuisinejournal.food.CuisineFoodDefinition.isDrink(stack);
        }
    }

    public String translationKey() {
        return titleKey == null ? "cuisinejournal.category." + id.getNamespace() + "." + id.getPath() : titleKey;
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (items.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
            return true;
        }
        for (TagKey<Item> tag : tags) {
            if (stack.is(tag)) {
                return true;
            }
        }
        return false;
    }

    public boolean matches(Item item) {
        return matches(new ItemStack(item));
    }

    /**
     * Resolves the display subcategory without ever dropping an item. Legacy
     * categories behave as one implicit subcategory; configured categories get
     * a stable reserved fallback for items matching none of their children.
     */
    public Subcategory subcategoryOf(ItemStack stack) {
        if (subcategories.isEmpty()) {
            return new Subcategory(id, translationKey(), false, icon, tags, items);
        }
        // Explicit item lists (including manual assignments) beat tag matches.
        ResourceLocation itemId = stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId != null) {
            for (Subcategory subcategory : subcategories) {
                if (subcategory.items().contains(itemId)) {
                    return subcategory;
                }
            }
        }
        for (Subcategory subcategory : subcategories) {
            if (subcategory.matches(stack)) {
                return subcategory;
            }
        }
        for (Subcategory subcategory : subcategories) {
            if (subcategory.matchesFallback(stack)) return subcategory;
        }
        ResourceLocation fallbackId = new ResourceLocation(id.getNamespace(), id.getPath() + "/_other");
        return new Subcategory(fallbackId, "cuisinejournal.category.fallback", false,
                icon, List.of(), Set.of());
    }

    @SafeVarargs
    private static FoodCategory builtIn(String path, int priority, TagKey<Item>... tags) {
        return new FoodCategory(new ResourceLocation(Constants.MOD_ID, path),
                List.of(tags), priority, Set.of(), null, List.of(), null, false);
    }

    public static FoodCategory fromJson(ResourceLocation id, JsonObject json) {
        int priority = json.has("priority") ? json.get("priority").getAsInt() : 0;
        ResourceLocation icon = json.has("icon")
                ? ResourceLocation.tryParse(json.get("icon").getAsString()) : null;
        if (json.has("icon") && (icon == null || !BuiltInRegistries.ITEM.containsKey(icon))) {
            throw new IllegalArgumentException("invalid category icon");
        }
        Set<ResourceLocation> items = new LinkedHashSet<>(readIds(json, "items"));
        List<TagKey<Item>> tags = readIds(json, "tags").stream()
                .map(tag -> TagKey.create(Registries.ITEM, tag)).toList();
        List<Subcategory> subcategories = readSubcategories(id, json);
        if (items.isEmpty() && tags.isEmpty() && subcategories.isEmpty()) {
            throw new IllegalArgumentException("category needs items or tags");
        }
        return new FoodCategory(id, tags, priority, items, icon, subcategories, null, false);
    }

    private static List<Subcategory> readSubcategories(ResourceLocation parentId, JsonObject json) {
        if (!json.has("subcategories")) {
            return List.of();
        }
        JsonArray values = json.getAsJsonArray("subcategories");
        List<Subcategory> result = new ArrayList<>();
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (var value : values) {
            if (!value.isJsonObject()) {
                throw new IllegalArgumentException("subcategory must be an object");
            }
            JsonObject subJson = value.getAsJsonObject();
            if (!subJson.has("id") || !subJson.get("id").isJsonPrimitive()) {
                throw new IllegalArgumentException("subcategory needs a local id");
            }
            String localId = subJson.get("id").getAsString();
            if (localId.isEmpty() || localId.equals("_other") || localId.indexOf(':') >= 0) {
                throw new IllegalArgumentException("subcategory id must be local: " + localId);
            }
            ResourceLocation subId = ResourceLocation.tryParse(
                    parentId.getNamespace() + ":" + parentId.getPath() + "/" + localId);
            if (subId == null || !ids.add(subId)) {
                throw new IllegalArgumentException("duplicate or invalid subcategory id: " + localId);
            }

            ResourceLocation icon = subJson.has("icon")
                    ? ResourceLocation.tryParse(subJson.get("icon").getAsString()) : null;
            if (subJson.has("icon") && (icon == null || !BuiltInRegistries.ITEM.containsKey(icon))) {
                throw new IllegalArgumentException("invalid subcategory icon");
            }
            Set<ResourceLocation> items = new LinkedHashSet<>(readIds(subJson, "items"));
            List<TagKey<Item>> tags = readIds(subJson, "tags").stream()
                    .map(tag -> TagKey.create(Registries.ITEM, tag)).toList();
            boolean drinkFallback = false;
            if (subJson.has("drink_fallback")) {
                var valueFlag = subJson.get("drink_fallback");
                if (!valueFlag.isJsonPrimitive() || !valueFlag.getAsJsonPrimitive().isBoolean()) {
                    throw new IllegalArgumentException("drink_fallback must be a boolean");
                }
                drinkFallback = valueFlag.getAsBoolean();
            }
            if (items.isEmpty() && tags.isEmpty() && !drinkFallback) {
                throw new IllegalArgumentException("subcategory needs items or tags");
            }

            boolean hasName = subJson.has("name");
            boolean hasTranslationKey = subJson.has("translation_key");
            if (hasName && hasTranslationKey) {
                throw new IllegalArgumentException("subcategory cannot define both name and translation_key");
            }
            String titleKey;
            boolean literalTitle;
            if (hasName) {
                titleKey = subJson.get("name").getAsString();
                literalTitle = true;
            } else if (hasTranslationKey) {
                titleKey = subJson.get("translation_key").getAsString();
                literalTitle = false;
            } else {
                titleKey = "cuisinejournal.category." + subId.getNamespace() + "." + subId.getPath();
                literalTitle = false;
            }
            if (titleKey.isEmpty()) {
                throw new IllegalArgumentException("subcategory title cannot be empty");
            }
            result.add(new Subcategory(subId, titleKey, literalTitle, icon, tags, items, drinkFallback));
        }
        return List.copyOf(result);
    }

    private static List<ResourceLocation> readIds(JsonObject json, String field) {
        if (!json.has(field)) {
            return List.of();
        }
        JsonArray values = json.getAsJsonArray(field);
        List<ResourceLocation> ids = new ArrayList<>();
        for (var value : values) {
            ResourceLocation id = ResourceLocation.tryParse(value.getAsString());
            if (id == null) {
                throw new IllegalArgumentException("invalid " + field + " id: " + value);
            }
            ids.add(id);
        }
        return ids;
    }

    /**
     * Built-in ingredient categories.
     *
     * <p>Both the Forge {@code forge:} convention and the Fabric {@code c:}
     * convention are listed for every category, because the audit found the
     * Fabric port of Farmer's Delight populates both and the Forge build only
     * populates {@code forge:}.</p>
     */
    public static List<FoodCategory> builtInIngredientCategories() {
        List<FoodCategory> categories = new ArrayList<>();
        categories.add(builtIn("ingredient/fish", 10,
                CulinaryTags.item("forge", "raw_fishes"),
                CulinaryTags.item("forge", "cooked_fishes"),
                CulinaryTags.item("c", "raw_fishes"),
                CulinaryTags.item("c", "cooked_fishes")));
        categories.add(builtIn("ingredient/meat", 20,
                CulinaryTags.item("forge", "raw_meat"),
                CulinaryTags.item("forge", "cooked_beef"),
                CulinaryTags.item("forge", "cooked_pork"),
                CulinaryTags.item("forge", "cooked_chicken"),
                CulinaryTags.item("forge", "cooked_mutton"),
                CulinaryTags.item("forge", "cooked_bacon"),
                CulinaryTags.item("c", "raw_meats"),
                CulinaryTags.item("c", "cooked_meats")));
        categories.add(builtIn("ingredient/dairy", 30,
                CulinaryTags.item("forge", "milk"),
                CulinaryTags.item("c", "milks")));
        categories.add(builtIn("ingredient/egg", 40,
                CulinaryTags.item("forge", "eggs"),
                CulinaryTags.item("c", "eggs")));
        categories.add(builtIn("ingredient/grain", 50,
                CulinaryTags.item("forge", "grain"),
                CulinaryTags.item("forge", "bread"),
                CulinaryTags.item("forge", "dough"),
                CulinaryTags.item("forge", "pasta"),
                CulinaryTags.item("c", "grains"),
                CulinaryTags.item("c", "dough")));
        categories.add(builtIn("ingredient/fruit", 60,
                CulinaryTags.item("forge", "fruits"),
                CulinaryTags.item("forge", "berries"),
                CulinaryTags.item("c", "fruits"),
                CulinaryTags.item("c", "berries")));
        categories.add(builtIn("ingredient/vegetable", 70,
                CulinaryTags.item("forge", "vegetables"),
                CulinaryTags.item("forge", "salad_ingredients"),
                CulinaryTags.item("c", "vegetables"),
                CulinaryTags.item("c", "salad_ingredients")));
        categories.add(builtIn("ingredient/crop", 80,
                CulinaryTags.item("forge", "crops"),
                CulinaryTags.item("forge", "seeds"),
                CulinaryTags.item("c", "crops"),
                CulinaryTags.item("c", "seeds")));
        return categories;
    }
}
