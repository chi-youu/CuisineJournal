package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.food.tooltip.FoodTooltipComponent;
import com.chi__you.cuisinejournal.food.tooltip.FoodBarScale;
import com.chi__you.cuisinejournal.client.tooltip.MasteryTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static com.chi__you.cuisinejournal.food.tooltip.TooltipText.*;

/** Server-derived display values expanded into pixel-based client components. */
public final class CuisineFoodTooltip {
    public static final String PANEL_MARKER = "cuisinejournal:panel";
    private static final float DURATION_RANGE_MINUTES = 40.0F;
    private static final float DRINK_RANGE = 0.6F;
    private record Key(ResourceLocation item, int stars) {}
    private static final Map<Key, CuisineFoodInfoPackets.Response> CACHE = new HashMap<>();
    private record HostKey(ResourceLocation item,String variant){}
    private static final Map<HostKey,CuisineFoodInfoPackets.Response> HOST_CACHE=new HashMap<>();
    private static final Map<HostKey,Long> HOST_REQUESTED=new HashMap<>();
    public static void accept(CuisineFoodInfoPackets.HostResponse response){
        var key=new HostKey(response.item(),response.variant());if(HOST_CACHE.size()>1024)HOST_CACHE.clear();
        HOST_CACHE.put(key,response.food());HOST_REQUESTED.remove(key);
    }
    private record FruitKey(ResourceLocation item, int stars, List<String> fruits) {}
    private static final Map<FruitKey, CuisineFoodInfoPackets.Response> FRUIT_CACHE = new HashMap<>();
    private static final Map<FruitKey,Long> FRUIT_REQUESTED = new HashMap<>();
    private static final Pattern AQUA_EFFECT_LINE = Pattern.compile(".+ [(][0-9]{2}:[0-9]{2}[)]$");

    private CuisineFoodTooltip() {}

    public static void accept(CuisineFoodInfoPackets.Batch batch) {
        if (batch.first()) clear();
        for (var response : batch.foods()) CACHE.put(new Key(response.item(),response.stars()), response);
    }

    public static void accept(CuisineFoodInfoPackets.FruitResponse response) {
        var key=new FruitKey(response.food().item(), response.food().stars(), response.fruits());
        if(FRUIT_CACHE.size()>=1024)FRUIT_CACHE.clear();FRUIT_CACHE.put(key,response.food());FRUIT_REQUESTED.remove(key);
    }

    public static void clear() { CACHE.clear(); FRUIT_CACHE.clear(); FRUIT_REQUESTED.clear(); HOST_CACHE.clear();HOST_REQUESTED.clear(); }

    public static boolean repeatable(ItemStack stack) {
        var data = info(stack);
        return data != null && data.repeatable();
    }

    public static CuisineFoodInfoPackets.Response info(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ResourceLocation sourceId = CuisineAlcohol.sourceId(stack);
        var stamp = com.chi__you.cuisinejournal.culinary.MealStamp.read(stack);
        var key = sourceId == null ? itemId : sourceId;
        if(FoodVariantIdentity.hasVariant(stack)){
            String variant=FoodVariantIdentity.variant(stack);var host=new HostKey(itemId,variant);
            var info=HOST_CACHE.get(host);if(info!=null)return info;
            long now=System.nanoTime()/1_000_000;
            HOST_REQUESTED.entrySet().removeIf(e->now-e.getValue()>10_000);
            Long last=HOST_REQUESTED.get(host);
            if(variant.length()<=16384&&(last==null||now-last>1500)&&HOST_REQUESTED.size()<1024){
                HOST_REQUESTED.put(host,now);com.chi__you.cuisinejournal.network.Network.toServer(new CuisineFoodInfoPackets.HostRequest(itemId,variant));
            }
            if(LetsDoFood.variant(stack)||KaleidoscopeFood.quality(stack)||IngredientProvenance.sources(stack)!=null
                    ||stack.hasTag()&&stack.getTag().contains(KaleidoscopeFood.SERVING,10))return null;
        }
        int stars = CuisineFoodQuality.stars(stack);
        List<String> fruits = FruitJellyFlavorBridge.fruits(stack);
        if (!fruits.isEmpty()) {
            FruitKey variant = new FruitKey(key, stars, fruits);
            var info = FRUIT_CACHE.get(variant);
            if (info != null) return info;
            long now=System.nanoTime()/1_000_000;FRUIT_REQUESTED.entrySet().removeIf(e->now-e.getValue()>10_000);
            Long last=FRUIT_REQUESTED.get(variant);
            if (FRUIT_REQUESTED.size() < 1024 && (last==null||now-last>2000)) {
                FRUIT_REQUESTED.put(variant,now);
                com.chi__you.cuisinejournal.network.Network.toServer(new CuisineFoodInfoPackets.FruitRequest(key, stars, fruits));
            }
        }
        return CACHE.getOrDefault(new Key(key,stars),CACHE.get(new Key(key,0)));
    }

    public static boolean hasPanel(ItemStack stack) {
        if (!CuisineFoodClientState.active() || !CuisineEdibility.isEdible(stack)) return false;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if ("youkaishomecoming:sake_bottle".equals(id.toString()) && CuisineAlcohol.sourceId(stack) == null) return false;
        return info(stack) != null;
    }

    public static void append(ItemStack stack, List<Component> lines) {
        if (CuisineFoodClientState.active() && !CuisineFoodClientState.preserveNativeEffects()
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("aquaculturedelight")) {
            lines.removeIf(line -> isAquaEffectLine(line.getString()));
        }
        if (!hasPanel(stack)) return;
        MasteryTooltip.colorName(stack, lines);
        var data = info(stack);
        MasteryTooltip.insertHeader(lines, stack, CuisineAlcohol.isAlcohol(stack) ? "alcohol" : data.drink() ? "drink" : "food");
        lines.add(Component.empty().withStyle(style -> style.withInsertion(PANEL_MARKER)));
    }

    public static List<TooltipComponent> panel(ItemStack stack) {
        if (!hasPanel(stack)) return List.of();
        var data = info(stack);
        boolean alcohol = CuisineAlcohol.isAlcohol(stack);
        List<TooltipComponent> rows = new ArrayList<>();
        // Classification and stars sit under the item name (see append), as in the text tooltip.
        rows.add(new FoodTooltipComponent.Divider(0));
        rows.add(new FoodTooltipComponent.TextLine(slotLine(data)));

        List<FoodTooltipComponent.StatBar> bars = statBars(data, alcohol);
        var font = Minecraft.getInstance().font;
        int labelWidth = bars.stream().mapToInt(row -> font.width(row.label())).max().orElse(0);
        int valueX = labelWidth + GAP + BAR_WIDTH + GAP;
        float instantHealing = DirectFoodHealing.total(stack, data);
        boolean hasExtras = instantHealing > 0;
        if (hasExtras) valueX = Math.max(valueX, font.width(text("instant_heal")) + GAP);
        for (var effect : data.effects()) {
            String id = effect.id().toString();
            if (BuiltInRegistries.MOB_EFFECT.getOptional(effect.id()).isEmpty()) continue;
            if (id.equals("minecraft:instant_health") || id.equals("minecraft:absorption")) {
                valueX = Math.max(valueX, font.width(text(id.endsWith("absorption") ? "absorption" : "instant_heal")) + GAP);
                hasExtras = true;
            } else if (id.equals("minecraft:health_boost")) {
                valueX = Math.max(valueX, GROUP_INDENT + font.width(text("extra_health")) + GAP);
                hasExtras = true;
            }
        }
        for (var bar : bars) rows.add(new FoodTooltipComponent.StatBar(bar.label(), labelWidth,
                valueX, bar.fill(), bar.fillColor(), bar.ghostFill(), bar.ghostColor(), bar.value(), bar.overflowFill()));
        if (hasExtras) rows.add(new FoodTooltipComponent.Spacer(3));

        List<FoodTooltipComponent.GroupRow> grouped = new ArrayList<>();
        List<FoodTooltipComponent.EffectEntry> effects = levelBonusEntries(stack, data);
        // Instant heal first, the absorption shield right under it (no longer in the no-stack group).
        FoodTooltipComponent.ValueRow absorption = null;
        if (instantHealing > 0) rows.add(new FoodTooltipComponent.ValueRow(colored(text("instant_heal"), LABEL), valueX,
                specialValue(instantHealing, HEALTH)));
        for (var effect : data.effects()) {
            MobEffect type = BuiltInRegistries.MOB_EFFECT.getOptional(effect.id()).orElse(null);
            if (type == null) continue;
            String id = effect.id().toString();
            if (id.equals("minecraft:instant_health")) {
                // Included in the single healing row above, alongside direct host healing.
            } else if (id.equals("minecraft:absorption")) {
                absorption = new FoodTooltipComponent.ValueRow(colored(text("absorption"), LABEL), valueX,
                        specialValue(4.0F * effect.level(), GOLD));
            } else if (id.equals("minecraft:health_boost")) {
                grouped.add(new FoodTooltipComponent.GroupRow(colored(text("extra_health"), LABEL),
                        specialValue(4.0F * effect.level(), GOLD)));
            } else {
                if (data.levelBonuses().getOrDefault(effect.id(), 0) > 0) continue;
                int color = effectColor(type);
                MutableComponent line = colored(type.getDisplayName(), color);
                if (effect.level() > 1) line.append(" " + roman(effect.level()));
                line.append(" ").append(colored(Component.literal(effectDuration(effect.durationTicks())), MUTED));
                effects.add(new FoodTooltipComponent.EffectEntry(type, line));
            }
        }
        if (absorption != null) rows.add(absorption);
        if (!grouped.isEmpty()) rows.add(new FoodTooltipComponent.NoStackGroup(colored(text("no_stack"), DIM), grouped, valueX));
        if (!effects.isEmpty()) {
            rows.add(new FoodTooltipComponent.Divider(0));
            rows.add(new FoodTooltipComponent.EffectGrid(effects, effects.size() > 4 ? 2 : 1));
        }
        List<Component> details = MasteryTooltip.details(stack);
        var hint = MasteryTooltip.hint(stack);
        if (!details.isEmpty() || hint != null) {
            rows.add(new FoodTooltipComponent.Spacer(4));
            details.forEach(line -> rows.add(new FoodTooltipComponent.TextLine(line)));
            if (hint != null) rows.add(hint);
        }
        int panelWidth = rows.stream().mapToInt(row -> new com.chi__you.cuisinejournal.food.client.tooltip.ClientFoodTooltipComponent(
                (FoodTooltipComponent) row).getWidth(font)).max().orElse(0);
        rows.replaceAll(row -> row instanceof FoodTooltipComponent.Divider ? new FoodTooltipComponent.Divider(panelWidth) : row);
        return rows;
    }

    /**
     * The classification fragment Obscure Tooltips can append to its own
     * rarity label. Returning null keeps the normal standalone panel layout.
     */
    public static Component obscureClassification(ItemStack stack) {
        if (!hasPanel(stack)) return MasteryTooltip.shouldUseTextHeader(stack)
                ? MasteryTooltip.classification(stack, MasteryTooltip.kind(stack)) : null;
        var data = info(stack);
        boolean alcohol = CuisineAlcohol.isAlcohol(stack);
        return MasteryTooltip.joinClassification(MasteryTooltip.classificationParts(stack,
                alcohol ? "alcohol" : data.drink() ? "drink" : "food"));
    }

    /** The health/regen (or drink bonus) and duration bars, without layout; shared with the journal. */
    public static List<FoodTooltipComponent.StatBar> statBars(CuisineFoodInfoPackets.Response data, boolean alcohol) {
        List<FoodTooltipComponent.StatBar> bars = new ArrayList<>();
        if (data.drink()) {
            MutableComponent value = colored(text("bonus", Math.round(data.drinkBonus() * 100.0F)), DRINK);
            bars.add(bar("drink_bonus", data.drinkBonus() / DRINK_RANGE, DRINK,
                    0.0F,
                    DRINK_GHOST, value));
        } else {
            float healthFill = data.healthPoints() / 2.0F / FoodBarScale.HEALTH_HEARTS;
            float regenFill = data.regenPerSecond() / 2.0F / FoodBarScale.REGEN_HEARTS_PER_SECOND;
            int healthColor = FoodBarScale.valueColor(HEALTH, healthFill);
            int regenColor = FoodBarScale.valueColor(REGEN, regenFill);
            bars.add(bar("health", healthFill, HEALTH, 0, 0,
                    colored(text("hearts", hearts(data.healthPoints())), healthColor).append(heart(healthColor))));
            bars.add(bar("regen", regenFill, REGEN, 0, 0,
                    colored(Component.literal(String.format(Locale.ROOT, "%.2f", data.regenPerSecond() / 2.0F)), regenColor)
                            .append(heart(regenColor)).append(colored(text("per_second"), regenColor))));
        }
        var skills=com.chi__you.cuisinejournal.client.ClientJournalState.skills();
        int duration=(int)Math.max(20,Math.round(data.durationTicks()*(skills!=null&&skills.active()&&!data.drink()?skills.ordinaryDuration():1)));
        bars.add(bar("duration", (float) Math.sqrt(duration / 1200.0F / DURATION_RANGE_MINUTES),
                DURATION, 0, 0, colored(durationText(duration), DURATION)));
        return bars;
    }

    /** Server values for an item at a star level (falls back to ★0), or null before the catalogue arrives. */
    public static CuisineFoodInfoPackets.Response info(ResourceLocation item, int stars) {
        return CACHE.getOrDefault(new Key(item, stars), CACHE.get(new Key(item, 0)));
    }

    /** Bonus-only rows lead the effect list; shield values already communicate absorption. */
    public static List<FoodTooltipComponent.EffectEntry> levelBonusEntries(ItemStack stack, CuisineFoodInfoPackets.Response data) {
        List<FoodTooltipComponent.EffectEntry> rows = new ArrayList<>();
        if (data == null || data.levelBonuses().isEmpty()) return rows;
        java.util.Set<ResourceLocation> available = new java.util.HashSet<>();
        data.effects().forEach(effect -> available.add(effect.id()));
        var food = stack.getFoodProperties(null);
        if (food != null) for (var pair : food.getEffects()) {
            if (pair.getFirst() != null && pair.getSecond() > 0)
                available.add(BuiltInRegistries.MOB_EFFECT.getKey(pair.getFirst().getEffect()));
        }
        data.levelBonuses().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(bonus -> {
            if (!showLevelBonus(bonus.getKey(), bonus.getValue()) || !available.contains(bonus.getKey())) return;
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.getOptional(bonus.getKey()).orElse(null);
            if (effect != null) rows.add(new FoodTooltipComponent.EffectEntry(effect, levelBonusLine(effect, bonus.getValue())));
        });
        return rows;
    }

    public static boolean showLevelBonus(ResourceLocation effect, int bonus) {
        return bonus > 0 && !effect.toString().equals("minecraft:absorption");
    }

    public static Component levelBonusLine(MobEffect effect, int bonus) {
        return colored(effect.getDisplayName(), effectColor(effect))
                .append(" ").append(colored(Component.literal("+" + roman(bonus)), GOLD));
    }

    /** Shares bonus-first ordering with the dish detail panel. */
    public static List<FoodTooltipComponent.EffectEntry> effectEntries(CuisineFoodInfoPackets.Response data) {
        List<FoodTooltipComponent.EffectEntry> result = new ArrayList<>();
        for (var effect : data.effects()) {
            int bonus = data.levelBonuses().getOrDefault(effect.id(), 0);
            MobEffect type = BuiltInRegistries.MOB_EFFECT.getOptional(effect.id()).orElse(null);
            if (type != null && showLevelBonus(effect.id(), bonus))
                result.add(new FoodTooltipComponent.EffectEntry(type, levelBonusLine(type, bonus)));
        }
        for (var effect : data.effects()) {
            if (showLevelBonus(effect.id(), data.levelBonuses().getOrDefault(effect.id(), 0))) continue;
            MobEffect type = BuiltInRegistries.MOB_EFFECT.getOptional(effect.id()).orElse(null);
            if (type == null) continue;
            MutableComponent line = colored(type.getDisplayName(), effectColor(type));
            if (effect.level() > 1) line.append(" " + roman(effect.level()));
            if (!type.isInstantenous() && effect.durationTicks() > 0)
                line.append(" ").append(colored(Component.literal(effectDuration(effect.durationTicks())), MUTED));
            result.add(new FoodTooltipComponent.EffectEntry(type, line));
        }
        return result;
    }

    /** "占用 ■■　进食 1.6 秒" as the tooltip shows it. */
    public static Component slotSummary(CuisineFoodInfoPackets.Response data) { return slotLine(data); }

    private static Component slotLine(CuisineFoodInfoPackets.Response data) {
        MutableComponent line = data.drink() ? muted("drink_slot") : muted("uses")
                .append(" ").append(colored(Component.literal("■".repeat(Math.max(1, data.slotCost()))), 0xD9D2C5));
        if (data.eatingTicks() > 0) {
            var skills=com.chi__you.cuisinejournal.client.ClientJournalState.skills();
            float seconds = (float)(data.eatingTicks() / 20.0 * (skills!=null&&skills.active()?skills.eat():1));
            line.append("　").append(muted(data.drink() ? "drink_time" : "eat_time", String.format(Locale.ROOT, "%.1f", seconds)));
            if (seconds <= 1.0F) line.append(colored(text("fast"), REGEN));
            else if (seconds > 2.0F) line.append(colored(text("slow"), DURATION));
        }
        return line;
    }

    private static FoodTooltipComponent.StatBar bar(String label, float fill, int color,
                                                    float ghost, int ghostColor, Component value) {
        return new FoodTooltipComponent.StatBar(colored(text(label), LABEL), 0, 0, fill, color, ghost, ghostColor, value,
                label.equals("health") || label.equals("regen") ? FoodBarScale.overflow(fill) : 0);
    }

    private static Component specialValue(float points, int color) {
        return colored(text("added_hearts", hearts(points)), color).append(heart(color));
    }

    private static String hearts(float points) {
        float value = points / 2.0F;
        return value == Math.round(value) ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static Component durationText(int ticks) {
        return ticks < 1200 ? text("seconds", Math.max(1, ticks / 20)) : text("minutes", ticks / 1200);
    }

    private static String effectDuration(int ticks) {
        int minutes = ticks / 1200;
        int seconds = ticks / 20 % 60;
        return String.format(Locale.ROOT, "(%d:%02d)", minutes, seconds);
    }

    private static boolean isAquaEffectLine(String text) {
        return text.length() > 3 && text.charAt(0) == '§'
                && "0123456789abcdefklmnor".indexOf(Character.toLowerCase(text.charAt(1))) >= 0
                && AQUA_EFFECT_LINE.matcher(text.substring(2)).matches();
    }

    private static int effectColor(MobEffect effect) {
        return effect.isBeneficial() ? 0x9DB8FF : effect.getCategory() == MobEffectCategory.HARMFUL ? 0xFF8A7A : 0xBBBBBB;
    }

    private static String roman(int value) {
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i++) while (value >= values[i]) { value -= values[i]; result.append(symbols[i]); }
        return result.toString();
    }
}
