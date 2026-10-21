package com.chi__you.cuisinejournal.compat.jeed;

import com.chi__you.cuisinejournal.api.FoodDefinitionsReloadedEvent;
import net.minecraftforge.common.MinecraftForge;

/** Loaded only when both optional integrations are present. */
public final class CuisineJeedCompat {
    private CuisineJeedCompat() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(CuisineJeedCompat::onDefinitions);
    }

    private static void onDefinitions(FoodDefinitionsReloadedEvent event) {
        var recipes = event.getServer().getRecipeManager();
        recipes.replaceRecipes(JeedFoodProviderHelper.appendGeneratedProviders(recipes.getRecipes()));
    }
}
