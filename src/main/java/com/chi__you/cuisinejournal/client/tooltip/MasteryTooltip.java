package com.chi__you.cuisinejournal.client.tooltip;

import com.chi__you.cuisinejournal.food.CuisineAlcohol;
import com.chi__you.cuisinejournal.food.tooltip.FoodTooltipComponent;
import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.culinary.MealStamp;
import com.chi__you.cuisinejournal.mastery.MasteryRank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.chi__you.cuisinejournal.food.tooltip.TooltipText.*;

/** Shared mastery display data and the text-only fallback when no food panel exists. */
public final class MasteryTooltip {
    private MasteryTooltip() {}

    /** Placeholder line swapped for the keycap {@link FoodTooltipComponent.HintLine} when tooltip components are gathered. */
    public static final String HINT_MARKER = "cuisinejournal:hint";

    /** Text tooltip (Vanilla food mode, or no Valheim panel): same header and Shift keycap as the panel. */
    public static void append(ItemStack stack, List<Component> lines) {
        if (!showsHeader(stack)) return;
        colorName(stack, lines);
        insertHeader(lines, stack, kind(stack));
        lines.addAll(details(stack));
        if (hint(stack) != null) lines.add(Component.empty().withStyle(style -> style.withInsertion(HINT_MARKER)));
    }

    /** Whether the text tooltip carries the classification/stars header at all. */
    public static boolean showsHeader(ItemStack stack) {
        if (!enabled(stack)) return false;
        return ClientJournalState.dish(itemId(stack)) != null || meaningfulStamp(stack) != null;
    }

    public static String kind(ItemStack stack) {
        if(com.chi__you.cuisinejournal.food.ExquisitoParfaits.matches(stack))return "food";
        return CuisineAlcohol.isAlcohol(stack) ? "alcohol"
                : com.chi__you.cuisinejournal.food.CuisineFoodDefinition.isDrink(stack) ? "drink" : "food";
    }

    /**
     * Right under the item name, for both food modes: the classification ("食物", unless Obscure
     * Tooltips already shows it beside the rarity as "普通 · 食物") and then the stars.
     */
    public static void insertHeader(List<Component> lines, ItemStack stack, String kind) {
        List<Component> header = new ArrayList<>();
        if (!obscureTooltips()) header.add(classification(stack, kind));
        header.add(stars(displayStamp(stack)));
        lines.addAll(Math.min(1, lines.size()), header);
    }

    /** The text tooltip (not the Valheim panel) shows this item's header. */
    public static boolean shouldUseTextHeader(ItemStack stack) {
        return showsHeader(stack) && !com.chi__you.cuisinejournal.food.CuisineFoodTooltip.hasPanel(stack);
    }

    public static boolean obscureTooltips() {
        return com.chi__you.cuisinejournal.platform.Services.PLATFORM.isModLoaded("obscure_tooltips");
    }

    public static boolean enabled(ItemStack stack) {
        return !stack.isEmpty();
    }

    public static MealStamp meaningfulStamp(ItemStack stack) {
        MealStamp stamp = MealStamp.read(stack);
        return stamp != null && stamp.isMeaningful() ? stamp : null;
    }

    /** Display-only stamp for food that has no recorded cook. Never writes NBT. */
    public static MealStamp displayStamp(ItemStack stack) {
        if(!com.chi__you.cuisinejournal.food.CuisineGameplayRules.starsEnabled()) {
            var previous=MealStamp.read(stack);
            return new MealStamp(2,false,5,previous==null?null:previous.cookId(),previous==null?"":previous.cookName());
        }
        if(com.chi__you.cuisinejournal.food.CuisineFoodQuality.isQualitySample(stack))return MealStamp.read(stack);
        if(com.chi__you.cuisinejournal.food.LetsDoFood.mapped(stack))return com.chi__you.cuisinejournal.food.LetsDoFood.display(stack);
        int hostStars=com.chi__you.cuisinejournal.food.KaleidoscopeFood.stars(stack);
        if(hostStars>=0){var original=MealStamp.read(stack);
            boolean perfect=original!=null&&original.perfect();
            return new MealStamp(hostStars,perfect,hostStars,original==null?null:original.cookId(),original==null?"":original.cookName());
        }
        ResourceLocation source = CuisineAlcohol.sourceId(stack);
        if (!com.chi__you.cuisinejournal.food.CuisineFoodQuality.craftable(source == null ? itemId(stack) : source))
            return new MealStamp(2, false, MasteryRank.highestIndex(), null, "");
        MealStamp stamp = meaningfulStamp(stack);
        return stamp != null
                ? stamp
                : new MealStamp(0, false, MasteryRank.highestIndex(), null, "");
    }

    public static void colorName(ItemStack stack, List<Component> lines) {
        if (!com.chi__you.cuisinejournal.food.CuisineGameplayRules.starsEnabled()) return;
        MealStamp stamp = meaningfulStamp(stack);
        if (stamp != null && stamp.perfect() && !lines.isEmpty()) {
            // Apply the color to every run, including explicit rarity/name colors.
            MutableComponent name = Component.empty();
            lines.get(0).visit((style, part) -> {
                name.append(Component.literal(part).withStyle(style.withColor(PERFECT)));
                return java.util.Optional.empty();
            }, net.minecraft.network.chat.Style.EMPTY);
            lines.set(0, name);
        }
    }

    public static Component classification(ItemStack stack, String kind) {
        return joinClassification(classificationParts(stack, kind));
    }

    public static List<Component> classificationParts(ItemStack stack, String kind) {
        List<Component> parts = new ArrayList<>();
        parts.add(muted("kind." + kind));
        MealStamp stamp = meaningfulStamp(stack);
        if (stamp != null && stamp.perfect() && com.chi__you.cuisinejournal.food.CuisineGameplayRules.starsEnabled())
            parts.add(colored(text("perfect"), PERFECT));
        return parts;
    }

    public static MutableComponent joinClassification(List<Component> parts) {
        MutableComponent line = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) line.append(colored(Component.literal(" · "), MUTED));
            line.append(parts.get(i));
        }
        return line;
    }

    public static Component stars(MealStamp stamp) {
        MutableComponent line = Component.empty();
        boolean bonus = stamp.perfect() && stamp.effectiveStars() > stamp.stars();
        for (int i = 0; i < MasteryRank.highestIndex(); i++) {
            int color = i < stamp.stars() ? 0xFFC940 : bonus && i == stamp.stars() ? PERFECT : 0x4A4254;
            line.append(star(color));
        }
        if (!stamp.cookName().isEmpty()) line.append(" ").append(muted("cooked_by", stamp.cookName()));
        return line;
    }

    public static FoodTooltipComponent.HintLine hint(ItemStack stack) {
        var record = ClientJournalState.record(itemId(stack));
        if (record == null || !record.discovered() || showDetails(net.minecraft.client.gui.screens.Screen.hasShiftDown())) return null;
        return new FoodTooltipComponent.HintLine(muted("hint.before"),
                colored(Component.literal("Shift"), LABEL), muted("hint.after"));
    }

    public static boolean showDetails(boolean shiftDown) {
        return !com.chi__you.cuisinejournal.config.ConfigManager.client().tooltips.requireModifier || shiftDown;
    }

    public static List<Component> details(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        if (!enabled(stack)) return lines;
        ResourceLocation id = itemId(stack);
        var dish = ClientJournalState.dish(id);
        if (dish == null) return lines;
        var record = ClientJournalState.record(id);
        if (record == null || !record.discovered()) {
            lines.add(muted("undiscovered"));
        } else {
            if (!showDetails(net.minecraft.client.gui.screens.Screen.hasShiftDown())) return lines;
            List<Component> summary = new ArrayList<>();
            if (record.firstPreparedDay() != Long.MIN_VALUE) {
                summary.add(muted("first_cooked", colored(Component.literal(Long.toString(record.firstPreparedDay())), LABEL)));
            }
            if (!summary.isEmpty()) lines.add(join(summary));
            appendOdds(lines, id, meaningfulStamp(stack));
        }
        return lines;
    }

    private static void appendOdds(List<Component> lines, ResourceLocation dish, MealStamp stamp) {
        var rules = ClientJournalState.rules();
        if (rules == null || !rules.bonusesEnabled()) return;
        int max = MasteryRank.highestIndex();
        int cookStars = ClientJournalState.rank(dish).ordinal();
        int eatStars = stamp != null ? stamp.effectiveStars() : 0;
        if (eatStars > 0) {
            List<Component> parts = new ArrayList<>();
            if(rules.vanillaFood()) {
                parts.add(muted("odds.saturation_coefficient",number(percent(rules.vanillaSaturationMultiplierPerStar()*eatStars))));
                if(eatStars>=5&&rules.vanillaMaxStarSaturation()>0)parts.add(muted("odds.saturation",
                        number(String.format(Locale.ROOT,"%.1f",rules.vanillaMaxStarSaturation()))));
                if(stamp.perfect())parts.add(muted("odds.perfect_nutrition",
                        number(String.format(Locale.ROOT,"%.2f",rules.vanillaPerfectNutritionMultiplier()))));
            }
            if (!parts.isEmpty()) lines.add(join(parts));
        }
        if (com.chi__you.cuisinejournal.food.CuisineFoodQuality.craftable(dish)) {
            double scale = (double) cookStars / max;
            var selected = ClientJournalState.todayDish();
            var current = ClientJournalState.dish(dish);
            var todayDish = selected == null || selected.dish() == null ? null : ClientJournalState.dish(selected.dish());
            boolean today = current != null && todayDish != null && current.preparationTarget().equals(todayDish.preparationTarget());
            double chance = com.chi__you.cuisinejournal.progression.ProductionQuality.chance(
                    rules.perfectChance() * scale + (ClientJournalState.skills()!=null&&ClientJournalState.skills().active()?ClientJournalState.skills().perfect():0)
                            +signatureBonus(dish),today,1,
                    rules.minimumPerfectChance() + (ClientJournalState.skills()!=null&&ClientJournalState.skills().active()?ClientJournalState.skills().floor():0),
                    ClientJournalState.skills()!=null&&ClientJournalState.skills().active()?Math.max(0,.01-ClientJournalState.skills().batch()):.01);
            if (chance > 0) lines.add(muted("odds.perfect", number(percent(chance))));
            else if (eatStars <= 0 && cookStars <= 0) lines.add(muted("odds.none"));
        }
    }

    private static Component number(String value) { return colored(Component.literal(value), LABEL); }
    private static double signatureBonus(ResourceLocation dish){
        var features=ClientJournalState.journalFeatures();if(features==null||!features.eligible())return 0;
        var selected=ClientJournalState.dish(features.signature());var current=ClientJournalState.dish(dish);
        return selected!=null&&current!=null&&selected.preparationTarget().equals(current.preparationTarget())?features.bonus():0;
    }
    private static String percent(double fraction) {
        return java.math.BigDecimal.valueOf(fraction * 100).setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "%";
    }
    private static ResourceLocation itemId(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()); }

    private static Component join(List<Component> parts) {
        MutableComponent result = Component.empty();
        for (Component part : parts) {
            if (!result.getSiblings().isEmpty()) result.append("　");
            result.append(part);
        }
        return result;
    }

}
