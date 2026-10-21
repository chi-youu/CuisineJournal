package com.chi__you.cuisinejournal.command;

import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.config.ServerConfig;
import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import com.chi__you.cuisinejournal.culinary.MealRecord;
import com.chi__you.cuisinejournal.mastery.MasteryCurve;
import com.chi__you.cuisinejournal.mastery.MasteryRank;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import com.chi__you.cuisinejournal.recipe.MealEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Renders a profile as readable chat lines.
 *
 * <p>Shared by {@code /cuisinejournal stats}, {@code recipe}, {@code export} and
 * {@code admin inspect} so all four agree on what a profile looks like.</p>
 */
public final class ProfileReport {

    private ProfileReport() {
    }

    public static List<Component> lines(CulinaryProfile profile, String playerName) {
        ServerConfig config = ConfigManager.server();
        MasteryCurve masteryCurve = config.mastery.toCurve();
        int total = CulinaryRegistries.current().size();

        List<Component> lines = new ArrayList<>();
        lines.add(CuisineJournalCommands.header("cuisinejournal.command.stats.title"));
        lines.add(value("Chef", playerName));
        lines.add(value("Cooking level", String.valueOf(
                profile.cookingLevel(config.progression.toCurve()))));
        lines.add(value("Cooking XP", String.valueOf(profile.cookingXp())));
        lines.add(value("Recipes discovered",
                profile.discoveredCount() + (total > 0 ? " / " + total : "")));
        lines.add(value("Recipes mastered", String.valueOf(profile.masteredCount(masteryCurve))));
        lines.add(value("Meals prepared", String.valueOf(profile.stats().mealsPrepared())));
        lines.add(value("Meals personally eaten", String.valueOf(profile.stats().mealsEaten())));
        lines.add(value("Portions served", String.valueOf(profile.stats().portionsServed())));
        lines.add(value("Ingredients discovered",
                String.valueOf(profile.journalIngredientsDiscoveredCount())));
        lines.add(value("Cooking streak", profile.streaks().cookingStreak() + " day(s)"));
        lines.add(value("Variety streak", profile.streaks().varietyStreak() + " dish(es)"));

        MealRecord favourite = mostPrepared(profile);
        if (favourite != null) {
            lines.add(value("Most prepared",
                    favourite.target() + " — " + favourite.prepared()));
        }
        lines.add(value("Best day", profile.records().mostMealsInOneDay() + " meal(s)"));
        return lines;
    }

    public static List<Component> recipeLines(MealEntry entry, CulinaryProfile profile) {
        ServerConfig config = ConfigManager.server();
        MasteryCurve curve = config.mastery.toCurve();
        MealRecord record = profile.peekMeal(entry.target());
        MealRecord preparations=profile.preparationRecord(entry.target());

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(entry.translationKey())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        lines.add(value("Source", entry.sourceModId()));
        lines.add(value("Category", entry.category().id().toString()));
        lines.add(value("Methods", entry.methods().stream()
                .map(method -> method.id().toString()).toList().toString()));
        lines.add(value("Nutrition / saturation", entry.nutrition() + " / " + entry.saturation()));

        if (record == null && preparations==null) {
            lines.add(Component.translatable("cuisinejournal.command.recipe.never")
                    .withStyle(ChatFormatting.GRAY));
            return lines;
        }
        MasteryRank rank = profile.rankOf(entry.target(), curve);
        long points = profile.masteryPointsOf(entry.target());
        long next = curve.pointsForNextRank(points);
        boolean capped = !config.mastery.perDish() && rank.ordinal() >= profile.categoryRankCap(entry.target());
        lines.add(value("Mastery", Component.translatable(rank.translationKey()).getString()
                + (capped ? " (category cap)" : next < 0 ? " (max)"
                : " — " + curve.progressIntoRank(points) + " / " + next)));
        lines.add(value("Prepared", String.valueOf(preparations==null?0:preparations.prepared())));
        lines.add(value("Eaten", String.valueOf(record==null?0:record.eaten())));
        lines.add(value("Served", String.valueOf(record==null?0:record.served())));
        if (preparations!=null&&preparations.firstPreparedDay() != Long.MIN_VALUE) {
            lines.add(value("First prepared", "day " + preparations.firstPreparedDay()));
        }
        lines.add(value("Best day", (preparations==null?0:preparations.bestPreparedInOneDay()) + " prepared"));
        if (preparations!=null&&!preparations.preparationsByMethod().isEmpty()) {
            for (Map.Entry<CookingMethod, Long> method : preparations.preparationsByMethod().entrySet()) {
                lines.add(value("  via " + method.getKey().id(), String.valueOf(method.getValue())));
            }
        }
        return lines;
    }

    /** A denser dump intended to be copied out of chat. */
    public static List<Component> exportLines(CulinaryProfile profile, String playerName) {
        List<Component> lines = new ArrayList<>(lines(profile, playerName));
        lines.add(Component.literal("  Top dishes:").withStyle(ChatFormatting.GRAY));

        profile.meals().values().stream()
                .filter(MealRecord::hasEverBeenPrepared)
                .sorted(Comparator.comparingLong(MealRecord::prepared).reversed())
                .limit(10)
                .forEach(record -> lines.add(Component.literal(
                                "    " + record.target() + " — " + record.prepared())
                        .withStyle(ChatFormatting.WHITE)));

        if (!profile.badges().isEmpty()) {
            lines.add(value("Badges", profile.badges().stream()
                    .map(ResourceLocation::toString).toList().toString()));
        }
        return lines;
    }

    private static MealRecord mostPrepared(CulinaryProfile profile) {
        return profile.meals().values().stream()
                .filter(MealRecord::hasEverBeenPrepared)
                .max(Comparator.comparingLong(MealRecord::prepared))
                .orElse(null);
    }

    private static Component value(String label, String value) {
        return Component.literal("  " + label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }
}
