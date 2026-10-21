package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Predicate;

/** Always-on, targeted input corrections applied after resource-pack selection. */
public final class SquidRecipeCompat {
    private static final String TENTACLES = "forge:tentacles";
    private static final String RAW_SQUID = "forge:raw_fishes/calamari";
    private static final String RAW_TENTACLES = "cuisinejournal:raw_squid_tentacles";
    private static final String COOKED_SQUID = "forge:cooked_fishes/squid";
    private static final String GLOW_SQUID = "cuisinejournal:glow_squid";
    private static final ResourceLocation TAKOYAKI = id("miners_delight:cooking/takoyaki");
    private static final ResourceLocation OLD_TAKOYAKI = id("minersdelight:cooking/takoyaki");
    private SquidRecipeCompat() {}

    public static int apply(Map<ResourceLocation, JsonElement> recipes, Predicate<ResourceLocation> exists) {
        int changed = 0;
        changed += calamariDumplings(recipes, exists);
        if (exists.test(id("miners_delight:takoyaki"))) {
            JsonElement template = recipes.getOrDefault(TAKOYAKI, recipes.get(OLD_TAKOYAKI));
            if (template != null && template.isJsonObject()) {
                JsonObject copy = template.deepCopy().getAsJsonObject();
                boolean edited = recipeInputs(copy, Set.of("forge:foods/tentacles",
                        "forge:raw_fishes/squid/tentacles"), Set.of(), TENTACLES);
                if (edited || copy.equals(recipes.get(TAKOYAKI))) {
                    if (!copy.equals(recipes.get(TAKOYAKI))) { recipes.put(TAKOYAKI, copy); changed++; }
                    if (recipes.remove(OLD_TAKOYAKI) != null) changed++;
                }
            }
        }
        changed += edit(recipes, id("rusticdelight:calamari_roll"),
                Set.of("rusticdelight:calamari_roll_ingredients"), Set.of(), RAW_SQUID);
        changed += edit(recipes, id("culturaldelights:calamari_roll"),
                Set.of("forge:raw_fishes/squid/tentacles"), Set.of("culturaldelights:raw_calamari"), RAW_SQUID);
        changed += edit(recipes, id("oceansdelight:tentacle_on_a_stick"),
                Set.of(), Set.of("oceansdelight:tentacles"), RAW_TENTACLES);
        changed += edit(recipes, id("crabbersdelight:squid_kebob"),
                Set.of("crabbersdelight:cooked_squid"), Set.of(), COOKED_SQUID);
        for (String path : List.of("squid_and_pickles", "seafood_skewer"))
            changed += edit(recipes, id("oceanic_delight:" + path),
                    Set.of(), Set.of("oceanic_delight:grilled_squid_tentacles"), COOKED_SQUID);
        for (String path : List.of("cave_hamburger", "tentacles_on_a_stick"))
            changed += edit(recipes, id("miners_delight:" + path),
                    Set.of(), Set.of("miners_delight:baked_tentacles"), COOKED_SQUID);
        // Keep the original sandwich usable even without Delightful's replacement recipe.
        changed += edit(recipes, id("miners_delight:squid_sandwich"),
                Set.of("forge:foods/cooked_squid"), Set.of(), COOKED_SQUID);
        for (String recipe : List.of("oceanic_delight:glow_squid_stew", "farmersdelight:stockpot/glow_squid_stew"))
            changed += edit(recipes, id(recipe), Set.of(), Set.of("oceanic_delight:glow_squid_tentacles"), GLOW_SQUID);
        for (String method : List.of("campfire", "smelting", "smoking")) {
            ResourceLocation source = id("oceanic_delight:squid_tentacles_" + method);
            JsonElement original = recipes.get(source);
            if (original == null || !original.isJsonObject()) continue;
            JsonObject normal = original.deepCopy().getAsJsonObject();
            if (!cookingInput(normal, "oceanic_delight:squid_tentacles")) continue;
            if (exists.test(id("oceanic_delight:squid_tentacles"))) { recipes.put(source, normal); changed++; }
            else if (recipes.remove(source) != null) changed++;
            if (exists.test(id("oceanic_delight:glow_squid_tentacles"))) {
                ResourceLocation glowId = glowRecipe(method);
                if (!recipes.containsKey(glowId)) {
                    JsonObject glow = original.deepCopy().getAsJsonObject();
                    cookingInput(glow, "oceanic_delight:glow_squid_tentacles");
                    recipes.put(glowId, glow); changed++;
                }
            }
        }
        return changed;
    }

    public static ResourceLocation glowRecipe(String method) {
        return id("cuisinejournal:compat/oceanic_delight/squid_tentacles_" + method + "_from_glow_squid");
    }

    private static int calamariDumplings(Map<ResourceLocation, JsonElement> recipes,
                                         Predicate<ResourceLocation> exists) {
        if (!exists.test(id("dumplings_delight:calamari_boiled_dumpling"))) return 0;
        int changed = 0;
        List<JsonObject> retained = new ArrayList<>();
        for (int variant = 1; variant <= 4; variant++) {
            var key = id("dumplings_delight:dumpling/calamari_boiled_dumpling_recipe_" + variant);
            var original = recipes.get(key);
            if (original == null || !original.isJsonObject()) continue;
            var copy = original.deepCopy().getAsJsonObject();
            if (!matches(copy, "type", Set.of("farmersdelight:cooking"))
                    || !copy.has("result") || !copy.get("result").isJsonObject()
                    || !matches(copy.getAsJsonObject("result"), "item",
                            Set.of("dumplings_delight:calamari_boiled_dumpling"))) continue;
            boolean edited = recipeInputs(copy, Set.of("forge:raw_squid", "dumplings_delight:raw_squid"),
                    Set.of("oceansdelight:tentacles", "miners_delight:tentacles", "culturaldelights:raw_calamari"), RAW_SQUID);
            if (!edited && !recipeInputs(copy, Set.of(RAW_SQUID), Set.of(), RAW_SQUID)) continue;
            // Deduplicate only equivalent variants; preserve unrelated datapack changes and guards.
            if (retained.contains(copy)) {
                recipes.remove(key);
                changed++;
            } else {
                retained.add(copy);
                if (edited) { recipes.put(key, copy); changed++; }
            }
        }
        return changed;
    }

    private static int edit(Map<ResourceLocation, JsonElement> recipes, ResourceLocation id,
                            Set<String> tags, Set<String> items, String target) {
        JsonElement original = recipes.get(id);
        if (original == null || !original.isJsonObject()) return 0;
        JsonObject copy = original.deepCopy().getAsJsonObject();
        if (!recipeInputs(copy, tags, items, target)) return 0;
        recipes.put(id, copy); return 1;
    }

    private static boolean recipeInputs(JsonObject recipe, Set<String> tags, Set<String> items, String target) {
        boolean changed = false;
        if (recipe.has("ingredients")) changed |= ingredients(recipe.get("ingredients"), tags, items, target);
        if (recipe.has("key") && recipe.get("key").isJsonObject())
            for (var entry : recipe.getAsJsonObject("key").entrySet())
                changed |= ingredients(entry.getValue(), tags, items, target);
        // Guards must follow their repaired ingredient, but unrelated conditions stay intact.
        changed |= guards(recipe, tags, target);
        for (JsonObject nested : nestedRecipes(recipe)) changed |= recipeInputs(nested, tags, items, target);
        return changed;
    }

    private static boolean ingredients(JsonElement value, Set<String> tags, Set<String> items, String target) {
        boolean changed = false;
        if (value.isJsonArray()) for (var child : value.getAsJsonArray()) changed |= ingredients(child, tags, items, target);
        else if (value.isJsonObject()) {
            JsonObject ingredient = value.getAsJsonObject();
            if (matches(ingredient, "tag", tags) || matches(ingredient, "item", items)) {
                ingredient.remove("item"); ingredient.addProperty("tag", target); changed = true;
            }
        }
        return changed;
    }

    private static boolean guards(JsonElement value, Set<String> tags, String target) {
        boolean changed = false;
        if (value.isJsonArray()) for (var child : value.getAsJsonArray()) changed |= guards(child, tags, target);
        else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("type") && object.get("type").getAsString().equals("forge:tag_empty")
                    && matches(object, "tag", tags)) { object.addProperty("tag", target); changed = true; }
            for (var entry : object.entrySet()) if (entry.getValue().isJsonArray() || entry.getValue().isJsonObject())
                changed |= guards(entry.getValue(), tags, target);
        }
        return changed;
    }

    private static boolean cookingInput(JsonObject recipe, String item) {
        boolean changed = false;
        if (recipe.has("ingredient") && recipe.get("ingredient").isJsonObject()
                && matches(recipe.getAsJsonObject("ingredient"), "tag", Set.of("oceanic_delight:squid_tentacles"))) {
            var ingredient = recipe.getAsJsonObject("ingredient");
            ingredient.remove("tag"); ingredient.addProperty("item", item); changed = true;
        }
        for (JsonObject nested : nestedRecipes(recipe)) changed |= cookingInput(nested, item);
        return changed;
    }

    private static List<JsonObject> nestedRecipes(JsonObject recipe) {
        var nested = new ArrayList<JsonObject>();
        if (recipe.has("recipes") && recipe.get("recipes").isJsonArray()) for (var branch : recipe.getAsJsonArray("recipes"))
            if (branch.isJsonObject() && branch.getAsJsonObject().has("recipe")
                    && branch.getAsJsonObject().get("recipe").isJsonObject())
                nested.add(branch.getAsJsonObject().getAsJsonObject("recipe"));
        return nested;
    }
    private static boolean matches(JsonObject object, String key, Set<String> values) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.getAsJsonPrimitive(key).isString()
                && values.contains(object.get(key).getAsString());
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
}
