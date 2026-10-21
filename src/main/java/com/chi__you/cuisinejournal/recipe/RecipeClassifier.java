package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns an arbitrary {@link Recipe} into a {@link RecipeEntry}, or explains why
 * it is not a dish.
 *
 * <p>Reads nothing but the vanilla {@code Recipe} interface, which is the whole
 * reason an unknown addon's cooking recipe classifies identically to Farmer's
 * Delight's own.</p>
 *
 * <p>Every call into third-party recipe code is guarded: a modded recipe whose
 * {@code getResultItem} or {@code getIngredients} throws is reported as
 * unclassified and skipped, never allowed to abort the reload.</p>
 */
public final class RecipeClassifier {

    private final EligibilityRules rules;
    private final RegistryAccess registryAccess;

    public RecipeClassifier(EligibilityRules rules, RegistryAccess registryAccess) {
        this.rules = rules;
        this.registryAccess = registryAccess;
    }

    /**
     * @param verdict   why the recipe was or was not accepted
     * @param entry     present only when {@code verdict} is
     *                  {@link EligibilityRules.Verdict#ELIGIBLE}
     * @param failed    the recipe threw while being inspected
     */
    public record Classification(EligibilityRules.Verdict verdict, RecipeEntry entry, boolean failed) {

        static Classification rejected(EligibilityRules.Verdict verdict) {
            return new Classification(verdict, null, false);
        }

        static Classification failure() {
            return new Classification(EligibilityRules.Verdict.NOT_FOOD, null, true);
        }

        public boolean isEligible() {
            return entry != null;
        }
    }

    public Classification classify(Recipe<?> recipe) {
        try {
            return classify(recipe, outputs(recipe, registryAccess).stream().findFirst().orElse(ItemStack.EMPTY), false);
        } catch (RuntimeException | LinkageError failure) {
            logFailure(null, failure);
            return Classification.failure();
        }
    }

    /** All declared cutting outputs, including food after a nonfood byproduct. */
    public List<Classification> classifyAll(Recipe<?> recipe) {
        try {
            List<ItemStack> outputs = outputs(recipe, registryAccess);
            return outputs.isEmpty() ? List.of(classify(recipe, ItemStack.EMPTY, false))
                    : outputs.stream().map(output -> classify(recipe, output, false)).toList();
        } catch (RuntimeException | LinkageError failure) {
            logFailure(null, failure);
            return List.of(Classification.failure());
        }
    }

    /** A confirmed portion's whole item may be inedible; all exclusion rules still apply. */
    public Classification classifyPreparation(Recipe<?> recipe) {
        try {
            return classify(recipe, outputs(recipe, registryAccess).stream().findFirst().orElse(ItemStack.EMPTY), true);
        } catch (RuntimeException | LinkageError failure) {
            logFailure(null, failure);
            return Classification.failure();
        }
    }

    public static List<ItemStack> outputs(Recipe<?> recipe, RegistryAccess access) {
        String host=recipe.getClass().getName();
        if(host.equals("com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe")){
            Object raw=HostReflection.invoke(recipe,"getResults");
            if(raw instanceof List<?> list)return list.stream().filter(ItemStack.class::isInstance).map(ItemStack.class::cast).map(ItemStack::copy).toList();
        }
        if(host.equals("com.chefmooon.ubesdelight.common.crafting.forge.BakingMatRecipeImpl")) {
            Object results=HostReflection.invoke(recipe,"getResultList");
            if(results instanceof List<?> list)return list.stream().filter(ItemStack.class::isInstance).map(ItemStack.class::cast)
                    .filter(stack->!stack.isEmpty()).map(ItemStack::copy).toList();
        }
        if(host.equals("net.ribs.vintagedelight.recipe.FermentingRecipe")) {
            List<ItemStack> result=new ArrayList<>();ItemStack main=recipe.getResultItem(access);
            if(main!=null&&!main.isEmpty())result.add(main.copy());
            Object extra=HostReflection.invoke(recipe,"getSecondaryResultItem");
            if(extra instanceof ItemStack stack&&!stack.isEmpty())result.add(stack.copy());
            return List.copyOf(result);
        }
        for (Class<?> type = recipe.getClass(); type != null; type = type.getSuperclass()) {
            if (!type.getName().equals("vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe")) continue;
            Object raw = HostReflection.invoke(recipe, "getResults");
            if (!(raw instanceof List<?> list)) throw new IllegalStateException("Missing cutting outputs");
            return list.stream().filter(ItemStack.class::isInstance).map(ItemStack.class::cast)
                    .filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
        }
        ItemStack output = recipe.getResultItem(access);
        if ((output == null || output.isEmpty()) && com.chi__you.foodinteractions.LycheeDisplayAccess.generated(recipe)) {
            var displays = com.chi__you.foodinteractions.LycheeDisplayAccess.outputs(recipe);
            if (!displays.isEmpty()) output = displays.get(0);
        }
        if (output == null || output.isEmpty()) output = FluidRecipeBridge.output(recipe);
        output = FluidRecipeBridge.servingOutput(recipe, output);
        if (com.chi__you.foodinteractions.LycheeDisplayAccess.freezing(recipe) && output != null
                && output.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
            var serving = HostReflection.invoke(blockItem.getBlock(),
                    "getServingItem", blockItem.getBlock().defaultBlockState());
            var count = HostReflection.invoke(blockItem.getBlock(), "getMaxServings");
            if (serving instanceof ItemStack stack && count instanceof Integer servings) output = stack.copyWithCount(servings);
        }
        return output == null || output.isEmpty() ? List.of() : List.of(output);
    }

    private Classification classify(Recipe<?> recipe, ItemStack output, boolean preparation) {
        ResourceLocation recipeId;
        ResourceLocation recipeTypeId;
        try {
            recipeId = recipe.getId();
            recipeTypeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        } catch (RuntimeException | LinkageError failure) {
            logFailure(null, failure);
            return Classification.failure();
        }
        if (recipeTypeId == null) {
            return Classification.rejected(EligibilityRules.Verdict.NOT_FOOD);
        }

        EligibilityRules.Verdict verdict = preparation && !output.isEmpty()
                ? HostItemAvailability.enabled(output.getItem())
                    ? rules.evaluate(recipeId, recipeTypeId, BuiltInRegistries.ITEM.getKey(output.getItem()), true, output::is)
                    : EligibilityRules.Verdict.EXCLUDED
                : rules.evaluate(recipeId, recipeTypeId, output);
        if (verdict != EligibilityRules.Verdict.ELIGIBLE) {
            return Classification.rejected(verdict);
        }

        ResourceLocation target = BuiltInRegistries.ITEM.getKey(output.getItem());
        if (target == null) {
            return Classification.rejected(EligibilityRules.Verdict.NOT_FOOD);
        }

        List<RecipeEntry.IngredientSlot> slots;
        try {
            slots = readIngredients(recipe);
        } catch (RuntimeException | LinkageError failure) {
            logFailure(recipeId, failure);
            // The dish is still real even if its inputs are unreadable, so it
            // keeps its journal page with an empty ingredient list rather than
            // vanishing from the registry.
            slots = List.of();
        }

        if (rules.isUnpacking(recipeTypeId, slots.size(), output.getCount())) {
            return Classification.rejected(EligibilityRules.Verdict.EXCLUDED);
        }

        return new Classification(EligibilityRules.Verdict.ELIGIBLE, new RecipeEntry(
                recipeId,
                recipeTypeId,
                com.chi__you.foodinteractions.LycheeDisplayAccess.freezing(recipe)
                        ? CookingMethod.of("trailandtales_delight", "ice_cream") : CookingMethod.fromRecipeType(recipeTypeId),
                target,
                Math.max(1, output.getCount()),
                slots,
                target.getNamespace()), false);
    }

    static List<RecipeEntry.IngredientSlot> readIngredients(Recipe<?> recipe) {
        List<RecipeEntry.IngredientSlot> slots = new ArrayList<>();
        if(com.chi__you.foodinteractions.LycheeDisplayAccess.generated(recipe)
                && !com.chi__you.foodinteractions.LycheeDisplayAccess.freezing(recipe)) {
            for(ResourceLocation source:com.chi__you.foodinteractions.LycheeDisplayAccess.sourceItems(recipe))
                slots.add(new RecipeEntry.IngredientSlot(List.of(source),List.of(source),List.of()));
            return slots;
        }
        for (Ingredient ingredient : FluidRecipeBridge.inputs(recipe)) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            Set<ResourceLocation> items = new LinkedHashSet<>();
            for (ItemStack stack : ingredient.getItems()) {
                if (stack.isEmpty()) {
                    continue;
                }
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id != null) {
                    items.add(id);
                }
            }
            if (!items.isEmpty()) {
                Set<ResourceLocation> explicit = new LinkedHashSet<>();
                Set<ResourceLocation> tags = new LinkedHashSet<>();
                if (!readOrigin(ingredient, explicit, tags)) {
                    // Unreadable custom ingredient: treat every accepted item as named.
                    explicit.clear(); tags.clear(); explicit.addAll(items);
                }
                slots.add(new RecipeEntry.IngredientSlot(List.copyOf(items), List.copyOf(explicit),
                        List.copyOf(tags)));
            }
        }
        return slots;
    }

    /**
     * Recovers how a slot was written — named items and named tags — from the ingredient's
     * own JSON form. Vanilla and Forge's compound ingredients serialize as {@code {"item":…}},
     * {@code {"tag":…}} or arrays of those. Anything else (NBT, difference, modded types)
     * returns {@code false} and the caller falls back to the expanded item list.
     */
    static boolean readOrigin(Ingredient ingredient, Set<ResourceLocation> explicit, Set<ResourceLocation> tags) {
        try {
            return readOrigin(ingredient.toJson(), explicit, tags);
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    static boolean readOrigin(com.google.gson.JsonElement json, Set<ResourceLocation> explicit,
                              Set<ResourceLocation> tags) {
        if (json == null || json.isJsonNull()) return false;
        if (json.isJsonArray()) {
            boolean any = false;
            for (com.google.gson.JsonElement child : json.getAsJsonArray()) {
                if (!readOrigin(child, explicit, tags)) return false;
                any = true;
            }
            return any;
        }
        if (!json.isJsonObject()) return false;
        com.google.gson.JsonObject object = json.getAsJsonObject();
        if (object.has("type")) return false;
        if (object.has("tag") && object.get("tag").isJsonPrimitive()) {
            ResourceLocation tag = ResourceLocation.tryParse(object.get("tag").getAsString());
            if (tag == null) return false;
            tags.add(tag);
            return true;
        }
        if (object.has("item") && object.get("item").isJsonPrimitive()) {
            ResourceLocation item = ResourceLocation.tryParse(object.get("item").getAsString());
            if (item == null) return false;
            explicit.add(item);
            return true;
        }
        return false;
    }

    private static void logFailure(ResourceLocation recipeId, Throwable failure) {
        CuisineJournalLog.LOGGER.debug(
                "Skipping recipe {} during culinary classification; it threw while being inspected. "
                        + "The recipe itself is unaffected.",
                recipeId == null ? "<unknown>" : recipeId, failure);
    }
}
