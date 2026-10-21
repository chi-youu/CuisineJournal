package com.chi__you.cuisinejournal.client.journal;

import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.network.Packets;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * Shared formatting for the journal.
 *
 * <p>Dish names come from the item's own translation key, so an addon's food is
 * already localised in whatever languages that addon ships — Cuisine Journal never
 * duplicates a translation it does not own.</p>
 */
public final class JournalText {

    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.ROOT);

    private JournalText() {
    }

    public static ItemStack stackOf(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * The item's full tooltip, exactly as inventories show it (name, effects, mod tooltips,
     * cuisine panel), with optional journal-only lines appended underneath.
     */
    public static void renderItemTooltip(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font,
                                         ResourceLocation id, java.util.List<Component> extra, int mx, int my) {
        ItemStack stack = stackOf(id);
        if (stack.isEmpty()) {
            java.util.List<Component> lines = new java.util.ArrayList<>();
            lines.add(nameOf(id));
            if (extra != null) lines.addAll(extra);
            g.renderComponentTooltip(font, lines, mx, my);
            return;
        }
        renderItemTooltip(g, font, stack, extra, mx, my);
    }

    /**
     * Same tooltip for an already-built stack, so callers can hand in a stack carrying data
     * that only exists in that context (a historical cook's stamp, for example).
     */
    public static void renderItemTooltip(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font,
                                         ItemStack stack, java.util.List<Component> extra, int mx, int my) {
        if (stack.isEmpty()) {
            return;
        }
        java.util.List<Component> lines = new java.util.ArrayList<>(
                net.minecraft.client.gui.screens.Screen.getTooltipFromItem(net.minecraft.client.Minecraft.getInstance(), stack));
        if (extra != null) lines.addAll(extra);
        g.renderTooltip(font, lines, stack.getTooltipImage(), stack, mx, my);
    }

    public static Component nameOf(ResourceLocation id) {
        ItemStack stack = stackOf(id);
        return stack.isEmpty() ? Component.literal(id.getPath()) : stack.getHoverName();
    }

    private static List<Packets.IngredientView> tagNameSource;
    private static Map<ResourceLocation, Packets.IngredientView> tagNames = Map.of();

    /** Resolves recipe tag aliases through the same named entries used by the ingredient journal. */
    public static Component tagName(ResourceLocation id) {
        var entry = tagEntry(id);
        if (entry != null && entry.displayName() != null && !entry.displayName().isBlank())
            return Component.literal(entry.displayName());
        return entry == null ? tagName(id, List.of()) : tagName(entry.item(), entry.tagIds());
    }

    /** A merged recipe-tag group takes precedence over its single-member source tag. */
    public static Component trackedIngredientName(ResourceLocation tag, List<ResourceLocation> candidates, ResourceLocation icon) {
        if (tag == null) return nameOf(icon);
        var entry = tagEntry(tag);
        if (entry != null && entry.tagIds().stream().distinct().count() > 1)
            return tagName(tag);
        return candidates.size() == 1 ? nameOf(icon) : tagName(tag);
    }

    private static Packets.IngredientView tagEntry(ResourceLocation id) {
        var ingredients = ClientJournalState.ingredients();
        if (ingredients != tagNameSource) {
            var index = new HashMap<ResourceLocation, Packets.IngredientView>();
            for (var entry : ingredients) if (entry.tag()) {
                index.putIfAbsent(CulinaryRegistry.tagKey(entry.item()), entry);
                for (var alias : entry.tagIds())
                    index.putIfAbsent(CulinaryRegistry.tagKey(alias), entry);
            }
            tagNames = index;
            tagNameSource = ingredients;
        }
        return tagNames.get(CulinaryRegistry.tagKey(id));
    }

    public static Component tagName(ResourceLocation key, List<ResourceLocation> ids) {
        String translation = TagTranslationFilter.translationKey(key, ids, net.minecraft.locale.Language.getInstance()::has);
        if (translation != null) return Component.translatable(translation);
        return Component.literal(key.toString());
    }

    public static boolean untranslatedTag(Packets.IngredientView entry) {
        return TagTranslationFilter.untranslated(entry, net.minecraft.locale.Language.getInstance()::has);
    }

    public static String number(long value) {
        return NUMBERS.format(value);
    }

    /** Short form for tight spaces: 950, 1.8k, 12k, 3.4M. */
    public static String compact(long value) {
        if (value < 1_000L) return Long.toString(value);
        if (value < 10_000L) return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        if (value < 1_000_000L) return (value / 1_000L) + "k";
        return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
    }

    /** "84 / 127", or just "84" when there is no meaningful denominator. */
    public static String fraction(long value, long total) {
        return total > 0 ? number(value) + " / " + number(total) : number(value);
    }

    public static String percent(float fraction) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, fraction)) * 100.0F) + "%";
    }

    /**
     * Cozy mode hides raw numbers and percentages in favour of plain labels,
     * without changing any of the underlying data.
     */
    public static boolean cozy() {
        return false;
    }

    public static Component maybeNumber(long value) {
        return cozy() ? Component.empty() : Component.literal(number(value));
    }

    /**
     * A dish's mastery as filled and empty stars.
     *
     * <p>Text glyphs rather than a texture, because this mod ships no art
     *. The rank name is always shown alongside them, so rank is never
     * communicated by shape alone.</p>
     */
    public static String stars(ResourceLocation dish) {
        return starsOf(ClientJournalState.rank(dish).ordinal());
    }

    /** Stars for an explicit count, used for the rating stamped onto a stack. */
    public static String starsOf(int filled) {
        int total = com.chi__you.cuisinejournal.mastery.MasteryRank.highestIndex();
        StringBuilder text = new StringBuilder(total);
        for (int i = 0; i < total; i++) {
            text.append(i < filled ? '\u2605' : '\u2606');
        }
        return text.toString();
    }

    public static Component rankOf(ResourceLocation dish) {
        return Component.translatable(ClientJournalState.rank(dish).translationKey());
    }

    /** How far into the current mastery rank a dish is, as 0..1. */
    public static float masteryFraction(ResourceLocation dish) {
        if (!ClientJournalState.isDiscovered(dish)) {
            return 0.0F;
        }
        int rankCap = ClientJournalState.categoryRankCap(dish);
        if (rankCap > 0 && ClientJournalState.rank(dish).ordinal() >= rankCap) {
            return 1.0F;
        }
        long points = ClientJournalState.categoryMasteryPoints(dish);
        long needed = ClientJournalState.masteryCurve().pointsForNextRank(points);
        if (needed <= 0L) {
            return 1.0F;
        }
        return (float) ClientJournalState.masteryCurve().progressIntoRank(points) / needed;
    }

    /**
     * The workstation that stands for a cooking method (vanilla / Farmer's Delight blocks), or
     * a same-id item for other mods; empty when nothing matches.
     */
    public static ItemStack station(ResourceLocation method) {
        if (method == null) return ItemStack.EMPTY;
        for(var item:com.chi__you.cuisinejournal.recipe.WorkstationDisplay.candidates(method)){
            var stack=stackOf(item);if(!stack.isEmpty()&&!stack.is(net.minecraft.world.item.Items.AIR))return stack;
        }
        return ItemStack.EMPTY;
    }

    public static Component method(ResourceLocation methodId) {
        String key = "cuisinejournal.method." + methodId.getNamespace() + "." + methodId.getPath();
        if (net.minecraft.locale.Language.getInstance().has(key)) return Component.translatable(key);
        // Methods from other mods: fall back to a block/item with the same id, then to the id path.
        ItemStack station = station(methodId);
        if (!station.isEmpty() && !station.is(net.minecraft.world.item.Items.AIR)) return station.getHoverName();
        String path = methodId.getPath().replace('_', ' ');
        return Component.literal(path.isEmpty() ? methodId.toString()
                : Character.toUpperCase(path.charAt(0)) + path.substring(1));
    }

    /** A wearable badge, with a readable fallback for external definitions. */
    public static Component badgeName(ResourceLocation titleId) {
        String badgeKey="cuisinejournal.badge."+titleId.getNamespace()+"."+titleId.getPath();
        if(net.minecraft.locale.Language.getInstance().has(badgeKey))return Component.translatable(badgeKey);
        String key = "cuisinejournal.title." + titleId.getNamespace() + "." + titleId.getPath();
        if (net.minecraft.locale.Language.getInstance().has(key)) return Component.translatable(key);
        return Component.literal(titleId.getPath().replace('_', ' '));
    }

    public static ResourceLocation badgeIcon(ResourceLocation id){
        var stats=com.chi__you.cuisinejournal.client.ClientJournalState.statistics();
        if(stats!=null)for(var value:stats.achievements())if(value.badge()&&value.id().equals(id)&&value.icon()!=null)return value.icon();
        return ResourceLocation.parse("minecraft:gold_nugget");
    }

    public static Component category(ResourceLocation categoryId) {
        if (categoryId.getPath().equals("_mod")) return modName(categoryId.getNamespace());
        return Component.translatable(
                "cuisinejournal.category." + categoryId.getNamespace() + "." + categoryId.getPath());
    }

    public static Component modName(String modId) {
        return modName(modId, com.chi__you.cuisinejournal.platform.Services.PLATFORM.modDisplayName(modId));
    }

    public static Component modName(String modId, String fallback) {
        String key = "cuisinejournal.mod_name." + modId;
        return net.minecraft.client.resources.language.I18n.exists(key)
                ? Component.translatable(key) : Component.literal(fallback == null ? modId : fallback);
    }
}
