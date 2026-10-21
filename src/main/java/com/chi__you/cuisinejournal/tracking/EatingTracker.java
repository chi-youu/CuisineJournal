package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.progression.ProgressionService;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Records meals a player personally eats.
 *
 * <p>Kept separate from preparation on purpose: a chef who cooks for a whole
 * server should see "Prepared 412 / Personally Eaten 91", not one merged
 * number.</p>
 */
public final class EatingTracker {

    private EatingTracker() {
    }

    public static void onItemEaten(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) {
            return;
        }
        var profiles=com.chi__you.cuisinejournal.culinary.ProfileManager.get();
        if(profiles!=null&&com.chi__you.cuisinejournal.food.CuisineEdibility.isFood(stack.getFoodProperties(player)))
            com.chi__you.cuisinejournal.event.CulinaryEvents.FOOD_EATEN.fire(new com.chi__you.cuisinejournal.event.CulinaryEvents.FoodEaten(
                    player,profiles.of(player),itemId,com.chi__you.cuisinejournal.culinary.CulinaryClock.day(player.server)));
        // A portion cut from a dish (cake slice) is that dish for the journal.
        if(CulinaryRegistries.current().preparationTarget(itemId).equals(itemId))
            itemId = CulinaryRegistries.current().dishIdentity(itemId);
        if (!CulinaryRegistries.current().isTracked(itemId)) {
            return;
        }
        // The stamp travels with the stack, so who cooked it survives being
        // traded, dropped or stored in a chest before it is eaten.
        ProgressionService.recordMeal(player, itemId,
                com.chi__you.cuisinejournal.culinary.MealStamp.read(stack));
    }
}
