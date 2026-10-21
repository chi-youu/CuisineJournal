package com.chi__you.cuisinejournal.client.journal;

import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.client.FlavorClientState;
import com.chi__you.cuisinejournal.recipe.FlavorModifiers;
import com.chi__you.cuisinejournal.recipe.FlavorPresets;
import com.chi__you.cuisinejournal.recipe.FlavorStage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared flavor wording and drawing: icons, one-line summaries, stage tables. */
public final class FlavorUi {
    private FlavorUi() {
    }

    public static boolean visible(ResourceLocation id) {
        if (!com.chi__you.cuisinejournal.food.CuisineGameplayRules.flavorsEnabled()) return false;
        return available(id);
    }

    private static boolean available(ResourceLocation id) {
        var entry = FlavorClientState.get(id);
        return entry != null && com.chi__you.cuisinejournal.recipe.FlavorAvailability.hasContent(
                entry.preset(), effect -> BuiltInRegistries.MOB_EFFECT.containsKey(effect));
    }

    public static boolean hasVisibleFlavor(com.chi__you.cuisinejournal.network.Packets.FlavorInfo info) {
        return com.chi__you.cuisinejournal.food.CuisineGameplayRules.flavorsEnabled() && hasListedFlavor(info);
    }

    /** Catalogue source classification survives a gameplay toggle; unavailable empty presets stay hidden. */
    public static boolean hasListedFlavor(com.chi__you.cuisinejournal.network.Packets.FlavorInfo info) {
        return info.source() && (info.legacy() || info.flavors().stream().anyMatch(FlavorUi::available))
                || info.inherited().stream().flatMap(binding -> binding.flavors().stream()).anyMatch(FlavorUi::available);
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("cuisinejournal.flavor." + key, args);
    }

    public static Component name(ResourceLocation id) {
        FlavorClientState.Entry entry = FlavorClientState.get(id);
        return entry == null ? tr("unknown", id.toString()) : Component.literal(entry.preset().name());
    }

    /**
     * The preset's item, else the native sprite of its first potion effect, else a leaf.
     */
    public static void icon(GuiGraphics g, FlavorPresets.Preset preset, int x, int y, int size) {
        // Order: chosen effect icon, chosen item, the flavor's first potion effect, the ❖ mark.
        MobEffect chosen = preset == null || preset.iconEffect() == null ? null : BuiltInRegistries.MOB_EFFECT.get(com.chi__you.cuisinejournal.food.CuisineEffectIds.resolve(preset.iconEffect()));
        if (chosen != null) { effectIcon(g, chosen, x, y, size); return; }
        ItemStack stack = preset == null || preset.icon() == null ? ItemStack.EMPTY : JournalText.stackOf(preset.icon());
        if (!stack.isEmpty() && !stack.is(Items.AIR)) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(size / 16F, size / 16F, 1F);
            g.renderFakeItem(stack, 0, 0);
            g.pose().popPose();
            return;
        }
        MobEffect effect = preset == null ? null : firstEffect(preset);
        if (effect != null) {
            effectIcon(g, effect, x, y, size);
            return;
        }
        leaf(g, x, y, size);
    }

    public static void effectIcon(GuiGraphics g, MobEffect effect, int x, int y, int size) {
        if (effect != null && size > 0) {
            g.blit(x, y, 0, size, size, Minecraft.getInstance().getMobEffectTextures().get(effect));
        }
    }

    /** Default mark for flavors without an icon: the same ❖ as the "风味库" button. */
    public static void leaf(GuiGraphics g, int x, int y, int size) {
        var font = Minecraft.getInstance().font;
        int w = font.width("❖");
        float scale = Math.min(1F, size / 9F);
        g.pose().pushPose();
        g.pose().translate(x + (size - w * scale) / 2F, y + (size - 8 * scale) / 2F, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font, "❖", 0, 0, JournalTheme.TEXT_ACCENT, false);
        g.pose().popPose();
    }

    private static MobEffect firstEffect(FlavorPresets.Preset preset) {
        for (FlavorStage stage : preset.stages())
            for (FlavorStage.Effect effect : stage.effects()) {
                MobEffect value = BuiltInRegistries.MOB_EFFECT.get(com.chi__you.cuisinejournal.food.CuisineEffectIds.resolve(effect.id()));
                if (value != null) return value;
            }
        return null;
    }

    public static FlavorStage stageFor(FlavorPresets.Preset preset, int stars) {
        FlavorStage stage = FlavorStage.selectStage(preset.stages(), stars);
        return stage == null && !preset.stages().isEmpty() ? preset.stages().get(0) : stage;
    }

    /** Every line of one stage: potion effects first, then modifiers with their target. */
    /**
     * One stage as short items: potion effects ("治愈 I", a faint "×0.5" only when it lasts part
     * of the food time), level bonuses ("力量 +2级"), then one item per stat with its flat and
     * percentage parts together ("回血 +0.05♥/秒 +20%"). Lay them out with {@link #wrapItems}.
     */
    public static List<Component> lines(FlavorStage stage) {
        List<Component> result = new ArrayList<>();
        if (stage == null) return result;
        String target = stage.effectTarget().equals("food") ? "food_only"
                : stage.effectTarget().equals("drink") ? "drink_only" : null;
        for (FlavorStage.Effect effect : stage.effects()) {
            MutableComponent line = effectLine(effect).copy();
            if (target != null) line.append(tr(target).withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT)));
            result.add(line);
        }
        for (FlavorStage.EffectLevelBonus bonus : stage.effectLevelBonuses())
        {
            MutableComponent line = effectName(bonus.id()).copy().append(tr("level_bonus", bonus.levels()));
            if (target != null) line.append(tr(target).withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT)));
            result.add(line);
        }
        modifierLines(result, stage.modifiers(), null);
        modifierLines(result, stage.foodModifiers(), "food_only");
        modifierLines(result, stage.drinkModifiers(), "drink_only");
        if (result.isEmpty()) result.add(tr("no_effect"));
        return result;
    }

    public static Component effectName(ResourceLocation id) {
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(com.chi__you.cuisinejournal.food.CuisineEffectIds.resolve(id));
        return effect == null ? Component.literal(id.toString()) : effect.getDisplayName();
    }

    public static Component effectLine(FlavorStage.Effect effect) {
        MutableComponent line = effectName(effect.id()).copy().append(" " + roman(effect.level()));
        if (ClientJournalState.vanillaFood())
            return line.append(Component.literal(" " + effect.durationSeconds() + "s").withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT)));
        if (effect.activeFraction() < 0.999F)
            line.append(Component.literal(" \u00d7" + trim(effect.activeFraction())).withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT)));
        return line;
    }

    private static void modifierLines(List<Component> out, FlavorModifiers m, String target) {
        stat(out, "health", m.healthPoints() == 0 ? null : hearts(m.healthPoints()) + "\u2665", m.healthMultiplier(), target);
        stat(out, "regen", m.regenPerSecond() == 0 ? null : hearts(m.regenPerSecond()) + "\u2665/s", m.regenMultiplier(), target);
        stat(out, "duration", m.durationSeconds() == 0 ? null : signed(m.durationSeconds()) + "s", m.durationMultiplier(), target);
        stat(out, "drink_bonus", null, m.drinkBonusMultiplier(), target);
    }

    /** "回血 +0.05♥/s +20%": the stat name once, then its flat and percentage parts. */
    private static void stat(List<Component> out, String key, String flat, double multiplier, String target) {
        boolean pct = Math.abs(multiplier) > 1e-9;
        if (flat == null && !pct) return;
        MutableComponent line = tr("stat." + key);
        if (flat != null) line.append(" " + flat);
        if (pct) line.append(" " + percent(multiplier));
        if (target != null) line.append(tr(target).withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT)));
        out.add(line);
    }

    /**
     * Lays items out in lines of at most {@code width} (in unscaled font pixels), breaking only
     * between items so a value never splits across lines. An item wider than a line is split.
     */
    public static List<net.minecraft.util.FormattedCharSequence> wrapItems(net.minecraft.client.gui.Font font,
                                                                            List<Component> items, int width) {
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        Component gap = Component.literal("   ");
        MutableComponent line = null;
        int lineWidth = 0;
        for (Component item : items) {
            int w = font.width(item);
            if (line != null && lineWidth + font.width(gap) + w <= width) {
                line.append(gap).append(item);
                lineWidth += font.width(gap) + w;
                continue;
            }
            if (line != null) lines.add(line.getVisualOrderText());
            if (w > width) {
                List<net.minecraft.util.FormattedCharSequence> parts = font.split(item, Math.max(1, width));
                lines.addAll(parts.subList(0, parts.size() - 1));
                // Keep the last fragment open so following items can join it? Simpler: close it.
                lines.add(parts.get(parts.size() - 1));
                line = null; lineWidth = 0;
                continue;
            }
            line = Component.empty().append(item);
            lineWidth = w;
        }
        if (line != null) lines.add(line.getVisualOrderText());
        return lines;
    }

    /** Health points (2 = one heart) shown as signed hearts. */
    public static String hearts(double points) { return signed(points / 2.0); }
    public static String percent(double fraction) { return signed(fraction * 100.0) + "%"; }

    public static String signed(double value) {
        String text = trim(Math.abs(value));
        return (value < 0 ? "−" : "+") + text;
    }

    public static String trim(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-6) return Long.toString(Math.round(value));
        String text = String.format(Locale.ROOT, "%.2f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public static String roman(int level) {
        String[] values = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level >= 1 && level <= values.length ? values[level - 1] : Integer.toString(level);
    }

    /** Full stage table for a hover tooltip; {@code current} (or -1) is highlighted. */
    public static List<Component> tooltip(FlavorPresets.Preset preset, int current) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(preset.name()).withStyle(s -> s.withColor(JournalTheme.TEXT_ACCENT)));
        if (!preset.description().isBlank())
            lines.add(Component.literal(preset.description()).withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT)));
        if (!preset.enabled()) lines.add(tr("disabled").withStyle(s -> s.withColor(JournalTheme.DANGER)));
        for (FlavorStage stage : preset.stages()) {
            boolean here = stage.minStars() == current;
            List<Component> body = lines(stage);
            MutableComponent row = Component.literal("★" + stage.minStars() + "  ")
                    .withStyle(s -> s.withColor(here ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT_DIM));
            for (int i = 0; i < body.size(); i++) {
                if (i > 0) row.append("   ");
                row.append(body.get(i).copy().withStyle(s -> s.withColor(here ? JournalTheme.TEXT : JournalTheme.DISCOVERY)));
            }
            lines.add(row);
        }
        if (preset.stages().size() > 1)
            lines.add(tr("stage_rule").withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT)));
        return lines;
    }
}
