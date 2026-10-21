package com.chi__you.cuisinejournal.progression;

import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.config.ServerConfig;
import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import com.chi__you.cuisinejournal.culinary.MealStamp;
import com.chi__you.cuisinejournal.culinary.ProfileManager;
import com.chi__you.cuisinejournal.event.CulinaryEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Persisted social rewards for the cook after a successful meal.
 */
public final class CookingBonuses {

    private static boolean registered;

    private CookingBonuses() {
    }

    public static synchronized void install() {
        if (registered) {
            return;
        }
        registered = true;
        CulinaryEvents.MEAL_EATEN.register(CookingBonuses::onMealEaten);
    }

    private static void onMealEaten(CulinaryEvents.MealEaten event) {
        ServerConfig config = ConfigManager.server();
        MealStamp stamp = event.stamp();
        if (stamp != null) creditCook(event, stamp, config);
    }

    /**
     * Pays the cook when somebody else eats their food.
     *
     * <p>This is what makes cooking for other people worth doing, and it needs
     * no "gave an item to a player" hook — the stamp already records who made
     * it, so the credit lands whenever and wherever it is eaten. Eating your
     * own cooking pays nothing extra; you were already credited for making it.</p>
     */
    private static void creditCook(CulinaryEvents.MealEaten event, MealStamp stamp,
                                   ServerConfig config) {
        java.util.UUID cookId = stamp.cookId();
        if (cookId == null || cookId.equals(event.player().getUUID())) {
            return;
        }
        ProfileManager profiles = ProfileManager.get();
        if (profiles == null) {
            return;
        }
        CulinaryProfile cook = profiles.of(cookId);
        var reward = cook.claimSocialReward(event.day(), event.meal(), config);
        cook.meal(event.meal()).recordServed(1L);
        ServerPlayer online = event.player().server.getPlayerList().getPlayer(cookId);
        ProgressionService.grantMasteryBonus(online, cook, event.meal(), reward.mastery(), event.day());
        cook.stats().addPortionsServed(1L);
        long xp = reward.xp();
        int levelBefore = cook.cookingLevel(config.progression.toCurve());
        int levelAfter = cook.addCookingXp(xp, config.progression.toCurve());
        cook.markDirty();

        if (online != null) {
            if (xp > 0) CulinaryEvents.COOKING_XP_GAINED.fire(new CulinaryEvents.CookingXpGained(
                    online, cook, xp, event.meal()));
            if (levelAfter > levelBefore) {
                cook.pushActivity(new com.chi__you.cuisinejournal.culinary.ActivityEntry(
                        com.chi__you.cuisinejournal.culinary.ActivityEntry.Type.LEVEL_UP, null, levelAfter, event.day()));
                CulinaryEvents.COOKING_LEVEL_CHANGED.fire(new CulinaryEvents.CookingLevelChanged(
                        online, cook, levelBefore, levelAfter));
            }
            online.displayClientMessage(Component.translatable("cuisinejournal.notify.served",
                    event.player().getDisplayName(),
                    Component.translatable(itemName(event.meal()))), true);
            CulinaryEvents.PORTIONS_SERVED.fire(new CulinaryEvents.PortionsServed(
                    online, cook, event.meal(), 1, event.day()));
        } else {
            // onJoin reloads the profile from disk, so persist before an offline cook can reconnect.
            profiles.flush(cookId);
        }
    }

    private static String itemName(net.minecraft.resources.ResourceLocation meal) {
        var item = BuiltInRegistries.ITEM.get(meal);
        return item == null ? meal.toString() : item.getDescriptionId();
    }

    /** Star rating for a dish, 0..5, used by tooltips and the journal. */
    public static int starsFor(CulinaryProfile profile, ResourceLocation dish) {
        return profile.rankOf(dish, ConfigManager.server().mastery.toCurve()).ordinal();
    }
}
