package com.chi__you.cuisinejournal.compat.jeed;

import net.mehvahdjukaar.jeed.recipes.EffectProviderRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import com.chi__you.cuisinejournal.api.CuisineFoodAPI;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Builds JEED providers from Cuisine Journal effects and retained native effects. */
public final class JeedFoodProviderHelper {
    private static final String GENERATED_PREFIX = "cuisinejournal:jeed/food/";
    private static final String LEGACY_PREFIX = "cys_effectpack:jeed/food/";

    private JeedFoodProviderHelper() {
    }

    public static Iterable<Recipe<?>> appendGeneratedProviders(Iterable<Recipe<?>> existing) {
        if (!CuisineFoodAPI.isRuntimeActive()) return existing;
        List<Recipe<?>> merged = new ArrayList<>();
        for (Recipe<?> recipe : existing) {
            String id = recipe.getId().toString();
            if (!id.startsWith(GENERATED_PREFIX) && !id.startsWith(LEGACY_PREFIX)) {
                merged.add(recipe);
            }
        }

        Map<MobEffect, LinkedHashSet<Item>> providers = new LinkedHashMap<>();
        addCuisineEffects(providers);
        addFoodPropertyEffects(providers);
        addKnownDynamicFoods(providers);

        for (Map.Entry<MobEffect, LinkedHashSet<Item>> entry : providers.entrySet()) {
            if (entry.getKey() == null || entry.getValue().isEmpty()) continue;
            NonNullList<Ingredient> ingredients = NonNullList.create();
            for (Item item : entry.getValue()) {
                ingredients.add(Ingredient.of(item));
            }
            ResourceLocation effectId = BuiltInRegistries.MOB_EFFECT.getKey(entry.getKey());
            if (effectId == null) continue;
            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                "cuisinejournal", "jeed/food/" + effectId.getNamespace() + "/" + effectId.getPath());
            merged.add(new EffectProviderRecipe(
                recipeId,
                entry.getKey(),
                ingredients,
                List.of(),
                List.of()
            ));
        }
        return merged;
    }

    private static void addCuisineEffects(Map<MobEffect, LinkedHashSet<Item>> providers) {
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (stack.getFoodProperties(null) == null) continue;
            for (var setting : CuisineFoodAPI.configuredEffects(stack)) {
                MobEffect effect = BuiltInRegistries.MOB_EFFECT.getOptional(com.chi__you.cuisinejournal.food.CuisineEffectIds.resolve(setting.id())).orElse(null);
                if (effect != null && setting.activeFraction() > 0.0F) add(providers, effect, item);
            }
        }
    }

    private static void addFoodPropertyEffects(Map<MobEffect, LinkedHashSet<Item>> providers) {
        for (Item item : BuiltInRegistries.ITEM) {
            try {
                if (!item.isEdible()) continue;
                FoodProperties food = item.getFoodProperties();
                if (food == null) continue;
                for (var pair : food.getEffects()) {
                    if (pair.getFirst() != null) add(providers, pair.getFirst().getEffect(), item);
                }
            } catch (Throwable ignored) {
                // 某些第三方动态物品可能只在实体上下文中提供食物属性。
            }
        }
    }

    private static void addKnownDynamicFoods(Map<MobEffect, LinkedHashSet<Item>> providers) {
        if (!CuisineFoodAPI.preservesNativeEffects()) return;
        addItemEffect(providers, "smc:bad_apple", "minecraft:bad_omen");
        addItemEffect(providers, "smc:chocliz", "smc:over_speed");
    }

    private static void addItemEffect(Map<MobEffect, LinkedHashSet<Item>> providers, String itemName, String effectName) {
        ResourceLocation itemId = ResourceLocation.tryParse(itemName);
        ResourceLocation effectId = ResourceLocation.tryParse(effectName);
        if (itemId == null || effectId == null) return;
        Item item = BuiltInRegistries.ITEM.get(itemId);
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(effectId);
        if (item != null && item != net.minecraft.world.item.Items.AIR && effect != null) {
            add(providers, effect, item);
        }
    }

    private static void add(Map<MobEffect, LinkedHashSet<Item>> providers, MobEffect effect, Item item) {
        providers.computeIfAbsent(effect, ignored -> new LinkedHashSet<>()).add(item);
    }
}
