package com.chi__you.cuisinejournal.food;

import net.minecraft.world.item.ItemStack;

/** Shared identity for actual-stack queries; host adapters own how each variant behaves. */
public final class FoodVariantIdentity {
    public static final String PENDING_PRODUCTION = "cuisinejournal:pending_production";
    public static final String BATCH_COUNT = "cuisinejournal:production_batch_count";
    public static final String INPUT_OWNERS = "cuisinejournal:kaleidoscope_input_owners";
    /** Equality ignores only journal bookkeeping; native quality/serving values still distinguish products. */
    public static final java.util.Set<String> PRODUCTION_BOOKKEEPING = java.util.Set.of(
            com.chi__you.cuisinejournal.culinary.MealStamp.ROOT, PENDING_PRODUCTION,
            IngredientProvenance.KEY, BATCH_COUNT, INPUT_OWNERS);
    /** A newly claimed result: keep host grades and sources, but never copy pending credit. */
    public static final java.util.Set<String> CLAIMED_RESULT = java.util.Set.of(
            IngredientProvenance.KEY, KaleidoscopeFood.QUALITY, KaleidoscopeFood.INHERITED_STARS,
            LetsDoFood.BEER_QUALITY);
    /** Plate to serving: inherit quality and sources; do not clone drink grades or production credit. */
    public static final java.util.Set<String> PLATE_SERVING = java.util.Set.of(
            IngredientProvenance.KEY, KaleidoscopeFood.QUALITY,
            com.chi__you.cuisinejournal.culinary.MealStamp.ROOT);
    /** A still-pending container output must carry its claim and exact sources. */
    public static final java.util.Set<String> PENDING_OUTPUT = java.util.Set.of(
            PENDING_PRODUCTION, IngredientProvenance.KEY);

    public static void copyKeys(ItemStack source, ItemStack target, java.util.Set<String> keys) {
        if (!source.hasTag()) return;
        for (String key : keys) if (source.getTag().contains(key))
            target.getOrCreateTag().put(key, source.getTag().get(key).copy());
    }

    public static boolean sameProduct(ItemStack first, ItemStack second, java.util.Set<String> bookkeeping) {
        if (!ItemStack.isSameItem(first, second)) return false;
        var left = first.hasTag() ? first.getTag().copy() : new net.minecraft.nbt.CompoundTag();
        var right = second.hasTag() ? second.getTag().copy() : new net.minecraft.nbt.CompoundTag();
        for (String key : bookkeeping) { left.remove(key); right.remove(key); }
        return left.equals(right);
    }
    private FoodVariantIdentity() {}

    public static String variant(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().toString() : "";
    }

    public static boolean hasVariant(ItemStack stack) {
        return stack.hasTag() && (stack.getTag().contains(CakeFoodCompat.SERVING, 10)
                || ModFoodCompat.host(stack) || stack.getTag().contains(KaleidoscopeFood.SERVING, 10))
                || LetsDoFood.variant(stack) || KaleidoscopeFood.quality(stack) || KaleidoscopeFood.drink(stack)
                || IngredientProvenance.sources(stack) != null;
    }
}
