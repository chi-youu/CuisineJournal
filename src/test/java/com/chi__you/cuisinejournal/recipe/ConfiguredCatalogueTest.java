package com.chi__you.cuisinejournal.recipe;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tests catalogue inclusion without bootstrapping a Forge game instance. */
public final class ConfiguredCatalogueTest {
    private static int checks;

    public static void main(String[] args) {
        ResourceLocation tea = id("tea"), existing = id("latte"), missing = id("missing"), material = id("material");
        FoodCategory category = new FoodCategory(id("drinks"), List.of(), 0, Set.of(tea), tea,
                List.of(new FoodCategory.Subcategory(id("drinks/tea"), "Tea", true, tea,
                        List.of(), Set.of(tea, existing, missing, material))), "Drinks", true);
        RecipeEntry recipe = new RecipeEntry(id("latte_recipe"), id("crafting"), CookingMethod.CRAFTING,
                existing, 1, List.of(), "test");
        Map<ResourceLocation, List<RecipeEntry>> grouped = new LinkedHashMap<>();
        grouped.put(existing, List.of(recipe));
        EligibilityRules rules = EligibilityRules.DEFAULT;
        int added = CulinaryRegistry.includeConfiguredItems(grouped, List.of(category),
                target -> !target.equals(missing) && rules.evaluateItem(target,
                        !target.equals(material), tag -> false) == EligibilityRules.Verdict.ELIGIBLE);
        check(added == 1 && grouped.size() == 2, "configured tea added once across root and subcategory");
        check(grouped.get(tea).isEmpty(), "catalogue-only item has no invented recipe");
        check(grouped.get(existing).equals(List.of(recipe)), "existing recipe remains intact");
        check(!grouped.containsKey(missing) && !grouped.containsKey(material), "missing and non-edible items excluded");
        check(CulinaryRegistry.includeConfiguredItems(grouped, List.of(category), target -> target.equals(tea)) == 0,
                "repeated inclusion does not duplicate entries");
        EligibilityRules denied = EligibilityRules.builder().denyItem(tea).build();
        Map<ResourceLocation, List<RecipeEntry>> excluded = new LinkedHashMap<>();
        CulinaryRegistry.includeConfiguredItems(excluded, List.of(category), target -> target.equals(tea)
                && denied.evaluateItem(target, true, tag -> false) == EligibilityRules.Verdict.ELIGIBLE);
        check(excluded.isEmpty(), "explicit item exclusion is respected");
        check(EligibilityRules.builder().denyMod("test").build().evaluateItem(tea, true, tag -> false)
                == EligibilityRules.Verdict.EXCLUDED, "mod exclusion is respected");
        check(EligibilityRules.builder().allowMod("other").build().evaluateItem(tea, true, tag -> false)
                == EligibilityRules.Verdict.EXCLUDED, "mod allowlist is respected");
        Map<ResourceLocation, List<RecipeEntry>> removed = new LinkedHashMap<>();
        check(CulinaryRegistry.includeConfiguredItems(removed, List.of(), target -> true) == 0 && removed.isEmpty(),
                "removing definitions clears catalogue-only entries on rebuild");
        FoodCategory.Subcategory fallback = new FoodCategory.Subcategory(id("drinks/other"), "Other drinks", true,
                null, List.of(), Set.of(), true);
        FoodCategory automatic = new FoodCategory(id("drinks"), List.of(), 0, Set.of(), null,
                List.of(fallback), "Drinks", true);
        Map<ResourceLocation, List<RecipeEntry>> drinks = new LinkedHashMap<>();
        drinks.put(existing, List.of(recipe));
        check(CulinaryRegistry.includeFallbackDrinks(drinks, List.of(automatic),
                List.of(existing, tea, missing, material), target -> target.equals(tea)) == 1,
                "fallback adds eligible drink without standard item recipe");
        check(drinks.get(tea).isEmpty() && drinks.get(existing).equals(List.of(recipe)),
                "fallback does not invent recipes or replace existing recipes");
        check(!drinks.containsKey(missing) && !drinks.containsKey(material),
                "fallback respects drink recognition and exclusions");
        check(CulinaryRegistry.includeFallbackDrinks(drinks, List.of(automatic), List.of(tea), target -> true) == 0,
                "fallback catalogue inclusion is idempotent");
        check(CulinaryRegistry.includeFallbackDrinks(new LinkedHashMap<>(), List.of(category),
                List.of(tea), target -> true) == 0, "no catch-all means no automatic catalogue inclusion");
        FoodCategory parsed = FoodCategory.fromJson(id("drinks"), com.google.gson.JsonParser.parseString(
                "{\"subcategories\":[{\"id\":\"other\",\"name\":\"Other\",\"drink_fallback\":true}]}").getAsJsonObject());
        check(parsed.subcategories().get(0).drinkFallback(), "fallback-only datapack category is valid");
        boolean invalid = false;
        try {
            FoodCategory.fromJson(id("bad"), com.google.gson.JsonParser.parseString(
                    "{\"subcategories\":[{\"id\":\"other\",\"drink_fallback\":\"true\"}]}").getAsJsonObject());
        } catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, "non-boolean catch-all is rejected");
        java.nio.file.Path temporary;
        try { temporary = java.nio.file.Files.createTempDirectory("cuisinejournal-drink-categories-"); }
        catch (java.io.IOException failure) { throw new AssertionError(failure); }
        try {
            GlobalCategoryStore.load(temporary);
            GlobalCategoryStore.apply(List.of(automatic));
            check(GlobalCategoryStore.editSubcategory(fallback.id().toString(), "Renamed", null),
                    "fallback subcategory can be renamed");
            check(GlobalCategoryStore.apply(List.of(automatic)).get(0).subcategories().get(0).drinkFallback(),
                    "appearance overrides preserve catch-all");
            check(GlobalCategoryStore.createRoot("test:moved", "Moved", null)
                    && GlobalCategoryStore.moveSubcategory(fallback.id().toString(), "test:moved"),
                    "fallback subcategory can move to another root");
            var moved = GlobalCategoryStore.apply(List.of(automatic));
            check(moved.stream().filter(root -> root.id().equals(id("moved")))
                    .flatMap(root -> root.subcategories().stream()).anyMatch(FoodCategory.Subcategory::drinkFallback),
                    "catch-all follows moved subcategory");
            check(GlobalCategoryStore.blockSubcategory(fallback.id().toString())
                    && GlobalCategoryStore.apply(List.of(automatic)).stream()
                    .flatMap(root -> root.subcategories().stream()).noneMatch(FoodCategory.Subcategory::drinkFallback),
                    "blocking fallback removes automatic classification");
            check(GlobalCategoryStore.restoreSubcategory(fallback.id().toString())
                    && GlobalCategoryStore.resetSubcategoryAppearance(fallback.id().toString()),
                    "fallback can be restored to its default parent");
            GlobalCategoryStore.apply(List.of(automatic));
            check(GlobalCategoryStore.blockRoot(automatic.id().toString())
                    && GlobalCategoryStore.apply(List.of(automatic)).stream()
                    .flatMap(root -> root.subcategories().stream()).noneMatch(FoodCategory.Subcategory::drinkFallback),
                    "blocking root removes automatic classification");
        } finally {
            try {
                java.nio.file.Files.deleteIfExists(temporary.resolve("cuisinejournal-categories.json"));
                java.nio.file.Files.deleteIfExists(temporary);
            } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        }
        System.out.println("ConfiguredCatalogueTest: " + checks + " checks passed");
    }

    private static ResourceLocation id(String path) { return ResourceLocation.parse("test:" + path); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
