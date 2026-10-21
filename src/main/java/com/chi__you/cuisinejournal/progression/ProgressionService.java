package com.chi__you.cuisinejournal.progression;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import com.chi__you.cuisinejournal.config.ConfigEnums;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.config.ServerConfig;
import com.chi__you.cuisinejournal.culinary.ActivityEntry;
import com.chi__you.cuisinejournal.culinary.CulinaryClock;
import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import com.chi__you.cuisinejournal.culinary.MealRecord;
import com.chi__you.cuisinejournal.culinary.MethodRecord;
import com.chi__you.cuisinejournal.culinary.ProfileManager;
import com.chi__you.cuisinejournal.event.CulinaryEvents;
import com.chi__you.cuisinejournal.mastery.MasteryCurve;
import com.chi__you.cuisinejournal.mastery.MasteryRank;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import com.chi__you.cuisinejournal.recipe.IngredientCatalogue;
import com.chi__you.cuisinejournal.recipe.IngredientCatalogueConfig;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistry;
import com.chi__you.cuisinejournal.recipe.MealEntry;
import com.chi__you.cuisinejournal.recipe.RecipeEntry;
import com.chi__you.cuisinejournal.tracking.CulinaryCredit;
import com.chi__you.cuisinejournal.tracking.ServingTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Applies a {@link CulinaryCredit} to a profile.
 *
 * <p>Everything that happens because a player cooked something happens here, in
 * one place, in one order: record the preparation, resolve discovery, credit
 * ingredients and methods, award mastery, award XP, update statistics, records
 * and streaks, then announce. Downstream systems listen on the event bus rather
 * than being called from here.</p>
 */
public final class ProgressionService {

    private ProgressionService() {
    }

    public static void credit(CulinaryCredit credit) {
        try {
            apply(credit);
        } catch (RuntimeException | LinkageError failure) {
            // Progression is a bonus system. A failure here must not propagate
            // into whatever the player was doing.
            CuisineJournalLog.LOGGER.error("Failed to apply culinary credit for {} x{}",
                    credit.meal(), credit.amount(), failure);
        }
    }

    private static void apply(CulinaryCredit credit) {
        ServerConfig config = ConfigManager.server();
        CulinaryRegistry registry = CulinaryRegistries.current();
        ResourceLocation preparation=registry.preparationTarget(credit.meal());
        if(!preparation.equals(credit.meal())) {
            // Cutting/serving an existing dish never becomes a preparation of its parent.
            if(IngredientCatalogueConfig.current().processing(credit.method().id())
                    ||credit.method().equals(CookingMethod.FEAST_SERVING))return;
            credit=new CulinaryCredit(credit.player(),preparation,credit.amount(),credit.method(),credit.source(),
                    credit.usedIngredients(),credit.automated(),credit.stamp());
        }
        if (credit.method().equals(CookingMethod.CRAFTING)
                && registry.isRepacking(credit.meal(), credit.usedIngredients())) return;
        MealEntry entry = registry.progressEntry(credit.meal());
        if (entry == null) {
            return;
        }

        double automationScale = automationScale(credit, config);
        if (automationScale <= 0.0) {
            return;
        }
        // Taking a portion out of a placed feast is serving, not cooking: the
        // dish was already prepared by whoever placed it.
        if (ServingTracker.recordIfServing(credit)) {
            return;
        }

        ProfileManager profiles = ProfileManager.get();
        if (profiles == null) {
            return;
        }
        ServerPlayer player = credit.player();
        CulinaryProfile profile = profiles.of(player);
        long day = CulinaryClock.day(player.server);
        MasteryCurve masteryCurve = config.mastery.toCurve();

        MealRecord record = profile.meal(credit.meal());
        boolean firstEver = !record.hasEverBeenPrepared();
        boolean perDish = config.mastery.perDish();
        MasteryRank rankBefore = perDish ? profile.rankOf(credit.meal(), masteryCurve)
                : profile.categoryRank(entry.masteryGroup(), masteryCurve);
        int levelBefore = profile.cookingLevel(config.progression.toCurve());

        record.recordPreparation(day, credit.amount(), credit.method());
        if((com.chi__you.cuisinejournal.food.KaleidoscopeFood.brewed(credit.meal())
                ||com.chi__you.cuisinejournal.food.LetsDoFood.beer(new net.minecraft.world.item.ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(credit.meal()))))&&credit.stamp()!=null)
            record.recordBrewedStars(credit.stamp().effectiveStars());

        boolean discovered = resolveDiscovery(profile, record, credit.meal(), day, config, player);
        boolean firstMethodUse = creditMethod(profile, credit, day, player);
        Ingredients ingredients = creditIngredients(profile, entry, credit, day, player);

        // Mastery points are always kept (original Meal Mastery rules), so switching the
        // star scope to DISH later uses the history the player already has.
        awardMastery(config, record, credit, entry);
        MasteryRank rankAfter = perDish ? profile.rankOf(credit.meal(), masteryCurve)
                : profile.categoryRank(entry.masteryGroup(), masteryCurve);
        if (rankAfter.ordinal() > rankBefore.ordinal()) {
            announceRank(player, profile, entry.masteryGroup(), credit.meal(),
                    rankBefore, rankAfter, record, day, perDish);
        }

        long xp = awardXp(profile, config, credit, day, firstEver, firstMethodUse,
                ingredients, masteryCurve, automationScale, player);

        // Method perks are applied without inserting or rearranging food.
        CookingRewards.onPrepared(player, profile, credit.meal(), credit.amount(), credit.method());

        profile.stats().addMealsPrepared(credit.amount());
        if (entry.category().id().getPath().equals("feast")) {
            profile.stats().addFeastsPrepared(credit.amount());
        }
        updateStreaksAndRecords(profile, config, credit.meal(), day);

        var stamp = credit.stamp() != null ? credit.stamp() : new com.chi__you.cuisinejournal.culinary.MealStamp(
                config.mastery.enabled ? CookingRewards.starsFor(profile, credit.meal(), config) : 2,
                false, MasteryRank.highestIndex(), player.getUUID(), player.getGameProfile().getName());
        profile.pushActivity(ActivityEntry.prepared(credit.meal(), xp, day, credit.amount(), stamp));
        profile.markDirty();

        CulinaryEvents.MEAL_PREPARED.fire(new CulinaryEvents.MealPrepared(
                player, profile, credit.meal(), credit.amount(), credit.method(), firstEver, day,stamp));
        if (discovered) {
            CulinaryEvents.RECIPE_DISCOVERED.fire(new CulinaryEvents.RecipeDiscovered(
                    player, profile, credit.meal(), day));
        }

        int levelAfter = profile.cookingLevel(config.progression.toCurve());
        if (levelAfter > levelBefore) {
            profile.pushActivity(new ActivityEntry(
                    ActivityEntry.Type.LEVEL_UP, null, levelAfter, day));
            CulinaryEvents.COOKING_LEVEL_CHANGED.fire(new CulinaryEvents.CookingLevelChanged(
                    player, profile, levelBefore, levelAfter));
        }
    }

    /**
     * Records that a player personally ate a tracked dish.
     *
     * @param stamp quality data the eaten stack carried, or {@code null}
     */
    public static void recordMeal(ServerPlayer player, ResourceLocation meal,
                                  com.chi__you.cuisinejournal.culinary.MealStamp stamp) {
        try {
            ServerConfig config = ConfigManager.server();
            CulinaryRegistry registry = CulinaryRegistries.current();
            if (!registry.isTracked(meal)) {
                return;
            }
            ProfileManager profiles = ProfileManager.get();
            if (profiles == null) {
                return;
            }
            CulinaryProfile profile = profiles.of(player);
            long day = CulinaryClock.day(player.server);

            MasteryRank rankBefore = profile.rankOf(meal, config.mastery.toCurve());
            int levelBefore = profile.cookingLevel(config.progression.toCurve());
            boolean firstMeal = profile.claimFirstMeal(meal);
            if (firstMeal) grantMasteryBonus(null, profile, meal, config.mastery.pointsPerFirstMeal, day);

            MealRecord record = profile.meal(meal);
            record.recordEaten(day, 1);
            profile.stats().addMealsEaten(1);
            if (config.mastery.enabled && config.mastery.pointsPerMeal > 0) {
                grantMasteryBonus(null, profile, meal, config.mastery.pointsPerMeal, day);
            }

            if (DiscoveryService.accepts(config.discovery,DiscoveryService.Trigger.EATEN)
                    && DiscoveryService.mark(profile,meal,day)) {
                CulinaryEvents.RECIPE_DISCOVERED.fire(new CulinaryEvents.RecipeDiscovered(
                        player, profile, meal, day));
            }
            long xp = XpEngine.eatingXp(config);
            if (xp > 0L) {
                profile.addCookingXp(xp, config.progression.toCurve());
            }
            profile.markDirty();

            announceProgress(player, profile, meal, rankBefore, day);
            int levelAfter = profile.cookingLevel(config.progression.toCurve());
            if (levelAfter > levelBefore) CulinaryEvents.COOKING_LEVEL_CHANGED.fire(
                    new CulinaryEvents.CookingLevelChanged(player, profile, levelBefore, levelAfter));
            CulinaryEvents.MEAL_EATEN.fire(
                    new CulinaryEvents.MealEaten(player, profile, meal, day, stamp));
        } catch (RuntimeException | LinkageError failure) {
            CuisineJournalLog.LOGGER.error("Failed to record a meal eaten: {}", meal, failure);
        }
    }

    // -------------------------------------------------------------- internals

    public static void grantMasteryBonus(ServerPlayer online, CulinaryProfile profile,
                                         ResourceLocation meal, long points, long day) {
        if (!ConfigManager.server().mastery.enabled || points <= 0) return;
        var registry = CulinaryRegistries.current();
        if (!registry.isTracked(meal)) return;
        MasteryRank before = profile.rankOf(meal, ConfigManager.server().mastery.toCurve());
        profile.meal(registry.preparationTarget(meal)).addBonusMasteryPoints(points);
        profile.markDirty();
        if (online != null) announceProgress(online, profile, meal, before, day);
    }

    private static void announceProgress(ServerPlayer player, CulinaryProfile profile, ResourceLocation meal,
                                          MasteryRank before, long day) {
        var config = ConfigManager.server();
        if (!config.mastery.enabled) return;
        var entry = CulinaryRegistries.current().progressEntry(meal);
        MasteryRank after = profile.rankOf(meal, config.mastery.toCurve());
        if (entry != null && after.ordinal() > before.ordinal()) announceRank(player, profile,
                entry.masteryGroup(), meal, before, after,
                profile.meal(CulinaryRegistries.current().preparationTarget(meal)), day, config.mastery.perDish());
    }

    private record Ingredients(int newlyDiscovered, int newVariants) {
    }

    private static double automationScale(CulinaryCredit credit, ServerConfig config) {
        if (!credit.automated()) {
            return 1.0;
        }
        return switch (config.automation.credit) {
            case NO_CREDIT -> 0.0;
            case REDUCED_CREDIT -> config.automation.reducedCreditMultiplier;
            case OWNER_CREDIT, FULL_CREDIT -> 1.0;
        };
    }

    private static boolean resolveDiscovery(CulinaryProfile profile, MealRecord record,
                                            ResourceLocation meal, long day, ServerConfig config,
                                            ServerPlayer player) {
        return DiscoveryService.accepts(config.discovery,DiscoveryService.Trigger.PREPARED)
                && DiscoveryService.mark(profile,meal,day);
    }

    private static boolean creditMethod(CulinaryProfile profile, CulinaryCredit credit, long day,
                                        ServerPlayer player) {
        CookingMethod method = credit.method();
        if (method == null || method.equals(CookingMethod.UNKNOWN)) {
            return false;
        }
        MethodRecord methodRecord = profile.method(method);
        boolean first = methodRecord.recordPreparation(day, credit.amount(), credit.meal());
        com.chi__you.cuisinejournal.challenge.DailyChallengeHistory.recordMethod(player.server,method);
        if (first) {
            profile.pushActivity(new ActivityEntry(
                    ActivityEntry.Type.METHOD_DISCOVERED, method.id(), 0L, day));
            CulinaryEvents.METHOD_DISCOVERED.fire(new CulinaryEvents.MethodDiscovered(
                    player, profile, method, day));
        }
        return first;
    }

    /**
     * Credits ingredients only where they were genuinely observed.
     *
     * <p>Two honest sources: slots that accept exactly one item (so there is
     * nothing to guess), and items seen leaving the player's inventory during
     * the same session. Tagged slots with no observed input are skipped rather
     * than credited to an arbitrary member.</p>
     */
      private static Ingredients creditIngredients(CulinaryProfile profile, MealEntry entry,
                                                 CulinaryCredit credit, long day,
                                                 ServerPlayer player) {
        Set<ResourceLocation> used = new LinkedHashSet<>();
        for (RecipeEntry recipe : entry.recipes()) {
            if (!recipe.method().equals(credit.method()) && entry.recipes().size() > 1) {
                continue;
            }
            for (RecipeEntry.IngredientSlot slot : recipe.ingredients()) {
                if (!slot.hasVariants()) {
                      ResourceLocation fixed = slot.items().get(0);
                      if (IngredientCatalogue.isUsableIngredient(fixed)) used.add(fixed);
                    continue;
                }
                for (ResourceLocation observed : credit.usedIngredients()) {
                    if (slot.accepts(observed)) {
                          if (IngredientCatalogue.isUsableIngredient(observed)) used.add(observed);
                    }
                }
            }
        }

        int discovered = 0;
        int variants = 0;
        MealRecord record = profile.meal(credit.meal());
        for (ResourceLocation ingredient : used) {
            CulinaryEvents.INGREDIENT_USED.fire(new CulinaryEvents.IngredientUsed(player,profile,ingredient,credit.amount(),day));
            if (profile.ingredient(ingredient)
                    .recordUse(day, credit.amount(), credit.meal(), credit.method())) {
                discovered++;
                profile.pushActivity(new ActivityEntry(
                        ActivityEntry.Type.INGREDIENT_DISCOVERED, ingredient, 0L, day));
                CulinaryEvents.INGREDIENT_DISCOVERED.fire(new CulinaryEvents.IngredientDiscovered(
                        player, profile, ingredient, day));
            }
            if (record.recordIngredientVariant(ingredient)) {
                variants++;
            }
        }
        profile.stats().addIngredientUses(used.size());
        return new Ingredients(discovered, variants);
    }

    private static void awardMastery(ServerConfig config, MealRecord record,
                                     CulinaryCredit credit, MealEntry entry) {
        if (!config.mastery.enabled) {
            return;
        }
        long points = (long) config.mastery.pointsPerPreparation * credit.amount();
        if (config.mastery.intendedMethodBonus > 0
                && credit.method().equals(entry.primaryMethod())) {
            points += (long) config.mastery.intendedMethodBonus * credit.amount();
        }
        record.addMasteryPoints(points);
    }

    private static void announceRank(ServerPlayer player, CulinaryProfile profile,
                                     ResourceLocation category, ResourceLocation meal,
                                     MasteryRank from, MasteryRank to,
                                     MealRecord record, long day, boolean perDish) {
        boolean fresh = perDish ? record.claimRankAnnouncement(to)
                : profile.claimCategoryRankAnnouncement(category, to);
        if (!fresh) {
            return;
        }
        profile.pushActivity(new ActivityEntry(
                to.isMastered() ? ActivityEntry.Type.MEAL_MASTERED : ActivityEntry.Type.MASTERY_RANK,
                meal, to.ordinal(), day));
        if (to.isMastered()) {
            long first = record.firstPreparedDay();
            long daysTaken = first == Long.MIN_VALUE ? -1L : Math.max(0L, day - first);
            profile.records().offerMastery(meal, day, daysTaken);
        }
        CulinaryEvents.MASTERY_RANK_CHANGED.fire(new CulinaryEvents.MasteryRankChanged(
                player, profile, meal, from, to));
    }

    private static long awardXp(CulinaryProfile profile, ServerConfig config, CulinaryCredit credit,
                                long day, boolean firstEver, boolean firstMethodUse,
                                Ingredients ingredients, MasteryCurve masteryCurve,
                                double automationScale, ServerPlayer player) {
        MealRecord record = profile.meal(credit.meal());
        // recordPreparation already ran, so today's count includes this batch.
        int priorThisPeriod = Math.max(0, record.preparedOnDay(day) - credit.amount());

        XpEngine.Result result = XpEngine.compute(new XpEngine.Inputs(
                credit.amount(),
                priorThisPeriod,
                firstEver,
                firstMethodUse,
                ingredients.newlyDiscovered(),
                ingredients.newVariants(),
                profile.experimentationXpOn(day),
                profile.masteredCount(masteryCurve)), config);
        profile.recordExperimentationXp(day, result.experimentationXpAwarded());

        long xp = Math.round(result.total() * automationScale);
        if (xp <= 0L) {
            return 0L;
        }
        profile.addCookingXp(xp, config.progression.toCurve());
        CulinaryEvents.COOKING_XP_GAINED.fire(new CulinaryEvents.CookingXpGained(
                player, profile, xp, credit.meal()));
        return xp;
    }

    private static void updateStreaksAndRecords(CulinaryProfile profile, ServerConfig config,
                                                ResourceLocation meal, long day) {
        if (config.streaks.clock != ConfigEnums.StreakClock.DISABLED) {
            int streak = profile.streaks().recordCookingDay(day, config.streaks.graceDays);
            profile.records().offerCookingStreak(streak);
        }
        if (config.streaks.varietyStreakEnabled) {
            int variety = profile.streaks().recordVariety(meal);
            profile.records().offerVarietyStreak(variety);
        }
        int uniqueToday = profile.streaks().recordDailyMeal(day, meal);
        profile.records().offerUniqueMealsInOneDay(uniqueToday, day);
        profile.records().offerMealsInOneDay(profile.streaks().mealsToday(), day);
    }
}
