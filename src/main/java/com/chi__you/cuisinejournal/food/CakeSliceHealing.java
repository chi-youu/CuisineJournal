package com.chi__you.cuisinejournal.food;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A successful Valheim serving heals once, independently of stars and native effects. */
public final class CakeSliceHealing {
    public static final float HEALTH_POINTS = 2F;
    public static final TagKey<Item> SLICES = ItemTags.create(
            ResourceLocation.fromNamespaceAndPath("cuisinejournal", "instant_healing_cake_slices"));

    private CakeSliceHealing() {}

    public static float points(ItemStack stack) {
        return stack != null && !stack.isEmpty() && CuisineFoodRuntime.activeForCurrentThread()
                && stack.is(SLICES) ? HEALTH_POINTS : 0;
    }

    public static void onEaten(ServerPlayer player, ItemStack stack) {
        float amount = points(stack);
        if (amount > 0) player.heal(amount);
    }
}
