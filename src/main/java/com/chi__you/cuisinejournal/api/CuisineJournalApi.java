package com.chi__you.cuisinejournal.api;

import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import com.chi__you.cuisinejournal.culinary.MealRecord;
import com.chi__you.cuisinejournal.culinary.ProfileManager;
import com.chi__you.cuisinejournal.event.CulinaryEvents;
import com.chi__you.cuisinejournal.mastery.MasteryRank;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import com.chi__you.cuisinejournal.recipe.MealEntry;
import com.chi__you.cuisinejournal.tracking.CulinaryCredit;
import com.chi__you.cuisinejournal.tracking.WorkstationRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.function.Consumer;

/**
 * The public surface other mods may use.
 *
 * <p>Recipes provide catalogue discovery; special production paths may still
 * need an adapter or explicit credit. Hosts can integrate unusual mechanics
 * and observe culinary progress without changing the journal's internals.</p>
 *
 * <p>Everything here is server-side and must be called on the server thread.</p>
 */
public final class CuisineJournalApi {

    /** Bumped when a method here changes shape. Additions do not bump it. */
    public static final int API_VERSION = 1;

    private CuisineJournalApi() {
    }

    // ------------------------------------------------------------ registration

    /**
     * Registers a block's cooking method. This does not install an adapter or
     * infer ownership for unknown inventory, fluid or world-interaction paths.
     */
    public static void registerWorkstation(ResourceLocation blockId, ResourceLocation methodId) {
        WorkstationRegistry.register(blockId, CookingMethod.of(methodId));
    }

    /**
     * Credits a player for producing a dish your mod made in a way Cuisine Journal
     * cannot observe.
     *
     * <p>Only call this when a specific player genuinely caused it. Crediting a
     * nearby player, or the owner of an automated machine, is exactly the
     * guessing this mod refuses to do.</p>
     *
     * @param dish   the produced item's id; ignored unless it is a tracked dish
     * @param amount how many were produced
     */
    public static void awardCooking(ServerPlayer player, ResourceLocation dish, int amount,
                                    ResourceLocation methodId) {
        if (player == null || dish == null || amount <= 0) {
            return;
        }
        com.chi__you.cuisinejournal.progression.ProgressionService.credit(new CulinaryCredit(
                player, dish, amount,
                methodId == null ? CookingMethod.UNKNOWN : CookingMethod.of(methodId),
                CulinaryCredit.Source.API, Set.of(), false));
    }

    // ------------------------------------------------------------------ queries

    /** @return {@code true} when this server has journal entries for the item */
    public static boolean isTrackedDish(ResourceLocation dish) {
        return CulinaryRegistries.current().isTracked(dish);
    }

    /** Every dish this server can make. Immutable and rebuilt on datapack reload. */
    public static Set<ResourceLocation> trackedDishes() {
        return CulinaryRegistries.current().targets();
    }

    /** @return the classified entry, or {@code null} when the dish is not tracked */
    public static MealEntry dish(ResourceLocation dish) {
        return CulinaryRegistries.current().entry(dish);
    }

    public static boolean hasDiscovered(ServerPlayer player, ResourceLocation dish) {
        CulinaryProfile profile = profileOf(player);
        return profile != null && profile.isDiscovered(dish);
    }

    public static MasteryRank masteryOf(ServerPlayer player, ResourceLocation dish) {
        CulinaryProfile profile = profileOf(player);
        return profile == null ? MasteryRank.UNFAMILIAR
                : profile.rankOf(dish, ConfigManager.server().mastery.toCurve());
    }

    public static long timesPrepared(ServerPlayer player, ResourceLocation dish) {
        CulinaryProfile profile = profileOf(player);
        if (profile == null) {
            return 0L;
        }
        MealRecord record = profile.preparationRecord(dish);
        return record == null ? 0L : record.prepared();
    }

    public static int cookingLevel(ServerPlayer player) {
        CulinaryProfile profile = profileOf(player);
        return profile == null ? 1
                : profile.cookingLevel(ConfigManager.server().progression.toCurve());
    }

    // ------------------------------------------------------------------- events

    public static void onMealPrepared(Consumer<CulinaryEvents.MealPrepared> listener) {
        CulinaryEvents.MEAL_PREPARED.register(listener);
    }

    public static void onRecipeDiscovered(Consumer<CulinaryEvents.RecipeDiscovered> listener) {
        CulinaryEvents.RECIPE_DISCOVERED.register(listener);
    }

    public static void onMasteryChanged(Consumer<CulinaryEvents.MasteryRankChanged> listener) {
        CulinaryEvents.MASTERY_RANK_CHANGED.register(listener);
    }

    public static void onCookingLevelChanged(Consumer<CulinaryEvents.CookingLevelChanged> listener) {
        CulinaryEvents.COOKING_LEVEL_CHANGED.register(listener);
    }

    public static void onChallengeCompleted(Consumer<CulinaryEvents.ChallengeCompleted> listener) {
        CulinaryEvents.CHALLENGE_COMPLETED.register(listener);
    }

    private static CulinaryProfile profileOf(ServerPlayer player) {
        ProfileManager manager = ProfileManager.get();
        return manager == null || player == null ? null : manager.of(player);
    }
}
