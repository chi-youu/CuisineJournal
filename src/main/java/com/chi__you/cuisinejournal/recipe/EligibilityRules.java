package com.chi__you.cuisinejournal.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides which recipes become journal entries.
 *
 * <p>The default rule is deliberately structural rather than curated: a recipe
 * counts when it produces something edible. That single rule is what makes an
 * unknown food addon work on install, and it is why there is no per-mod
 * database anywhere in this codebase.</p>
 *
 * <p>Everything on top of it is an escape hatch for packs: explicit allow and
 * deny lists by recipe id, item id, mod id and tag, plus an optional
 * "must carry one of these tags" restriction for servers that want a curated
 * journal.</p>
 */
public final class EligibilityRules {

    public static final EligibilityRules DEFAULT = builder().build();

    private final Set<ResourceLocation> allowedRecipes;
    private final Set<ResourceLocation> deniedRecipes;
    private final Set<ResourceLocation> allowedItems;
    private final Set<ResourceLocation> deniedItems;
    private final Set<String> allowedMods;
    private final Set<String> deniedMods;
    private final List<TagKey<Item>> requiredTags;
    private final List<TagKey<Item>> deniedTags;
    private final Set<ResourceLocation> deniedRecipeTypes;
    private final List<String> deniedItemPathSuffixes;
    private final boolean requireEdibleOutput;
    private final boolean ignoreUnpacking;

    private EligibilityRules(Builder builder) {
        this.allowedRecipes = Set.copyOf(builder.allowedRecipes);
        this.deniedRecipes = Set.copyOf(builder.deniedRecipes);
        this.allowedItems = Set.copyOf(builder.allowedItems);
        this.deniedItems = Set.copyOf(builder.deniedItems);
        this.allowedMods = Set.copyOf(builder.allowedMods);
        this.deniedMods = Set.copyOf(builder.deniedMods);
        this.requiredTags = List.copyOf(builder.requiredTags);
        this.deniedTags = List.copyOf(builder.deniedTags);
        this.deniedRecipeTypes = Set.copyOf(builder.deniedRecipeTypes);
        this.deniedItemPathSuffixes = List.copyOf(builder.deniedItemPathSuffixes);
        this.requireEdibleOutput = builder.requireEdibleOutput;
        this.ignoreUnpacking = builder.ignoreUnpacking;
    }

    public enum Verdict {
        ELIGIBLE,
        /** Explicitly excluded by configuration; reported separately by {@code /cj audit}. */
        EXCLUDED,
        /** Structurally not a dish — no edible output. The overwhelmingly common case. */
        NOT_FOOD
    }

    /**
     * Whether a recipe is a storage block being opened rather than a meal.
     *
     * <p>A crate of potatoes crafted back into potatoes produces food from a
     * crafting recipe, which is indistinguishable from cooking by output alone
     * — it was being credited as nine preparations. The shape gives it away:
     * one ingredient in, several of the same food out. Nothing anyone would
     * call cooking has that shape, and restricting the rule to vanilla's
     * crafting type keeps it away from cutting boards, where one input
     * legitimately yields several portions.</p>
     */
    public boolean isUnpacking(ResourceLocation recipeTypeId, int distinctIngredients,
                               int outputCount) {
        return ignoreUnpacking
                && outputCount > 1
                && distinctIngredients == 1
                && recipeTypeId != null
                && "minecraft".equals(recipeTypeId.getNamespace())
                && "crafting".equals(recipeTypeId.getPath());
    }

    public Verdict evaluate(ResourceLocation recipeId,
                            ResourceLocation recipeTypeId,
                            ItemStack output) {
        if (output == null || output.isEmpty()) {
            return Verdict.NOT_FOOD;
        }
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(output.getItem());
        if (!HostItemAvailability.enabled(output.getItem())) return Verdict.EXCLUDED;
        return evaluate(recipeId, recipeTypeId, itemId,
                com.chi__you.cuisinejournal.food.CuisineEdibility.isEdible(output), output::is);
    }

    /**
     * The rule engine proper, expressed without touching a live registry so it
     * stays unit-testable.
     *
     * @param itemId the output item's id, or {@code null} when there is no output
     * @param edible whether the output carries vanilla {@code FoodProperties}
     * @param hasTag tag membership test for the output
     */
    public Verdict evaluate(ResourceLocation recipeId,
                            ResourceLocation recipeTypeId,
                            ResourceLocation itemId,
                            boolean edible,
                            java.util.function.Predicate<TagKey<Item>> hasTag) {
        if (HostItemAvailability.hiddenByUnification(itemId)
                || deniedRecipes.contains(recipeId) || deniedRecipeTypes.contains(recipeTypeId)) {
            return Verdict.EXCLUDED;
        }
        // An explicit allow overrides every structural check, which is how a
        // pack registers an unusual dish that has no FoodProperties.
        if (allowedRecipes.contains(recipeId)) {
            return Verdict.ELIGIBLE;
        }
        return evaluateItem(itemId, edible, hasTag);
    }

    /** Catalogue-only entries have no recipe to check, but retain item exclusions. */
    public Verdict evaluateItem(ResourceLocation itemId, boolean edible,
                                java.util.function.Predicate<TagKey<Item>> hasTag) {
        if (itemId == null) {
            return Verdict.NOT_FOOD;
        }
        if (HostItemAvailability.hiddenByUnification(itemId)) return Verdict.EXCLUDED;
        if ("delightful".equals(itemId.getNamespace())
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(itemId)
                && !HostItemAvailability.enabled(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId))) {
            return Verdict.EXCLUDED;
        }
        if (deniedItemPathSuffixes.stream().anyMatch(itemId.getPath()::endsWith)) {
            return Verdict.EXCLUDED;
        }
        if (deniedItems.contains(itemId) || deniedMods.contains(itemId.getNamespace())) {
            return Verdict.EXCLUDED;
        }
        for (TagKey<Item> tag : deniedTags) {
            if (hasTag.test(tag)) {
                return Verdict.EXCLUDED;
            }
        }
        if (allowedItems.contains(itemId)) {
            return Verdict.ELIGIBLE;
        }
        if (!allowedMods.isEmpty() && !allowedMods.contains(itemId.getNamespace())) {
            return Verdict.EXCLUDED;
        }
        if (!requiredTags.isEmpty()) {
            boolean matched = false;
            for (TagKey<Item> tag : requiredTags) {
                if (hasTag.test(tag)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return Verdict.EXCLUDED;
            }
        }
        if (requireEdibleOutput && !edible) {
            return Verdict.NOT_FOOD;
        }
        return Verdict.ELIGIBLE;
    }

    public boolean deniesTag(ItemStack stack) {
        for (TagKey<Item> tag : deniedTags) if (stack.is(tag)) return true;
        return false;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Set<ResourceLocation> allowedRecipes = new LinkedHashSet<>();
        private final Set<ResourceLocation> deniedRecipes = new LinkedHashSet<>();
        private final Set<ResourceLocation> allowedItems = new LinkedHashSet<>();
        private final Set<ResourceLocation> deniedItems = new LinkedHashSet<>();
        private final Set<String> allowedMods = new LinkedHashSet<>();
        private final Set<String> deniedMods = new LinkedHashSet<>();
        private final List<TagKey<Item>> requiredTags = new java.util.ArrayList<>();
        private final List<TagKey<Item>> deniedTags = new java.util.ArrayList<>();
        private final Set<ResourceLocation> deniedRecipeTypes = new LinkedHashSet<>();
        private final List<String> deniedItemPathSuffixes = new java.util.ArrayList<>();
        private boolean requireEdibleOutput = true;
        private boolean ignoreUnpacking = true;

        public Builder allowRecipe(ResourceLocation id) {
            allowedRecipes.add(id);
            return this;
        }

        public Builder denyRecipe(ResourceLocation id) {
            deniedRecipes.add(id);
            return this;
        }

        public Builder allowItem(ResourceLocation id) {
            allowedItems.add(id);
            return this;
        }

        public Builder denyItem(ResourceLocation id) {
            deniedItems.add(id);
            return this;
        }

        public Builder allowMod(String modId) {
            allowedMods.add(modId);
            return this;
        }

        public Builder denyMod(String modId) {
            deniedMods.add(modId);
            return this;
        }

        public Builder requireTag(TagKey<Item> tag) {
            requiredTags.add(tag);
            return this;
        }

        public Builder denyTag(TagKey<Item> tag) {
            deniedTags.add(tag);
            return this;
        }

        public Builder denyRecipeType(ResourceLocation id) {
            deniedRecipeTypes.add(id);
            return this;
        }

        public Builder denyItemPathSuffix(String suffix) {
            if (suffix != null && !suffix.isBlank()) deniedItemPathSuffixes.add(suffix);
            return this;
        }

        public Builder ignoreUnpacking(boolean ignore) {
            this.ignoreUnpacking = ignore;
            return this;
        }

        public Builder requireEdibleOutput(boolean require) {
            this.requireEdibleOutput = require;
            return this;
        }

        public EligibilityRules build() {
            return new EligibilityRules(this);
        }
    }
}
