package com.chi__you.cuisinejournal.recipe;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * One classified recipe.
 *
 * <p>Several of these can point at the same {@link #masteryTarget()}: Farmer's
 * Delight alone ships three ways to get a beef patty (crafting, smoking,
 * campfire), and the design is explicit that those must not become three
 * journal pages. Method usage is still tracked per entry.</p>
 *
 * @param recipeId     the recipe's registry id
 * @param recipeTypeId the recipe type's registry id
 * @param method       the cooking method this recipe represents
 * @param masteryTarget the output item id that owns the mastery track
 * @param outputCount  how many items one completion produces
 * @param ingredients  one entry per ingredient slot
 * @param sourceModId  namespace of the output item, used for the journal's source label
 */
public record RecipeEntry(ResourceLocation recipeId,
                          ResourceLocation recipeTypeId,
                          CookingMethod method,
                          ResourceLocation masteryTarget,
                          int outputCount,
                          List<IngredientSlot> ingredients,
                          String sourceModId) {

    public RecipeEntry {
        ingredients = List.copyOf(ingredients);
    }

    /**
     * One ingredient slot, keeping how the recipe wrote it.
     *
     * <p>{@code items} is every accepted item (tags expanded); tracking and variant logic use it.
     * {@code explicitItems} are the items the recipe named directly, and {@code tags} the item
     * tags it named. The journal lists a dish under an item only when the item was named, and
     * under a tag when the tag was named — "egg" and "any egg" are different relationships.</p>
     *
     * @param items         every item accepted here, in registry order
     * @param explicitItems items written literally in the recipe
     * @param tags          item tags written in the recipe
     */
    public record IngredientSlot(List<ResourceLocation> items, List<ResourceLocation> explicitItems,
                                 List<ResourceLocation> tags) {
        public IngredientSlot {
            items = List.copyOf(items);
            explicitItems = List.copyOf(explicitItems);
            tags = List.copyOf(tags);
        }

        /** A slot whose origin is unknown: every accepted item counts as named. */
        public IngredientSlot(List<ResourceLocation> items) {
            this(items, items, List.of());
        }

        public boolean accepts(ResourceLocation item) {
            return items.contains(item);
        }

        public boolean hasVariants() {
            return items.size() > 1;
        }
    }
}
