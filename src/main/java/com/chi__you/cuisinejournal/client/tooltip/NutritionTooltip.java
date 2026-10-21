package com.chi__you.cuisinejournal.client.tooltip;

import com.chi__you.cuisinejournal.food.CuisineGameplayRules;
import com.chi__you.cuisinejournal.food.tooltip.FoodTooltipComponent;
import com.chi__you.cuisinejournal.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Obscure owns nutrition tooltips when installed; otherwise this row owns them. */
public final class NutritionTooltip {
    public static final String MARKER = "cuisinejournal:nutrition";
    public static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("cuisinejournal", "nutrition");
    private NutritionTooltip() {}

    public static FoodTooltipComponent.NutritionRow row(ItemStack stack) {
        if (stack.isEmpty() || !useOwnTooltip(CuisineGameplayRules.nutritionDisplayEnabled(),
                Services.PLATFORM.isModLoaded("obscure_api"))) return null;
        var properties = stack.getFoodProperties(Minecraft.getInstance().player);
        if (properties == null || !hasValues(properties.getNutrition(), properties.getSaturationModifier())) return null;
        return new FoodTooltipComponent.NutritionRow(properties.getNutrition(), properties.getSaturationModifier());
    }

    public static boolean useOwnTooltip(boolean enabled, boolean obscureInstalled) {
        return enabled && !obscureInstalled;
    }

    public static void append(ItemStack stack, List<Component> lines) {
        if (row(stack) == null) return;
        int index = Math.min(1, lines.size());
        if (MasteryTooltip.showsHeader(stack) || com.chi__you.cuisinejournal.food.CuisineFoodTooltip.hasPanel(stack))
            index = Math.min(lines.size(), index + (MasteryTooltip.obscureTooltips() ? 1 : 2));
        lines.add(index, Component.empty().withStyle(style -> style.withInsertion(MARKER)));
    }

    public static String saturationText(float modifier) {
        return (int)(Math.max(0, Float.isFinite(modifier) ? modifier : 0) * 100F) + "%";
    }

    public static boolean hasValues(int nutrition, float modifier) {
        return nutrition > 0 || Float.isFinite(modifier) && modifier > 0;
    }

    /** Same 12px icon advance, 11px bitmap height and ascent 9 as Obscure's food row. */
    public static Component format(int nutrition, float modifier) {
        var line = Component.empty();
        if (nutrition > 0) line.append("\uE100\uE102" + nutrition);
        if (Float.isFinite(modifier) && modifier > 0) line.append("\uE101" + saturationText(modifier));
        return line.withStyle(style -> style.withFont(FONT).withColor(0xFFFFFF));
    }
}
