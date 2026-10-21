package com.chi__you.cuisinejournal.client.journal;

import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.client.FlavorClientState;
import com.chi__you.cuisinejournal.culinary.MealStamp;
import com.chi__you.cuisinejournal.food.CuisineFoodClientState;
import com.chi__you.cuisinejournal.food.CuisineFoodTooltip;
import com.chi__you.cuisinejournal.food.CuisineVanillaBonuses;
import com.chi__you.cuisinejournal.food.client.tooltip.ClientFoodTooltipComponent;
import com.chi__you.cuisinejournal.food.tooltip.FoodTooltipComponent;
import com.chi__you.cuisinejournal.mastery.MasteryRank;
import com.chi__you.cuisinejournal.network.Packets;
import com.chi__you.cuisinejournal.recipe.FlavorStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The dish detail panel of the category browser: header, records, stars, attributes (with a
 * ★0–★5 switch), flavors (click to expand), recipes (one at a time, a cell per ingredient
 * position) and, in edit mode, the category block.
 */
public final class FoodDetailPanel {
    private FoodDetailPanel() { }

    // ------------------------------------------------------------ hit areas

    /** What a click in the panel asks the page to do. */
    public record Click(Kind kind, ResourceLocation id) {
        public enum Kind { HANDLED, RECIPES, USES, INGREDIENT, TAG, DISH }
    }

    private enum HotKind { DISH, SLOT, ACTION }

    /** An interactive area from the last render, in screen coordinates (scroll already applied). */
    private record Hotspot(int x, int y, int width, int height, HotKind kind, ResourceLocation item,
                           ResourceLocation tag, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
    }

    private static final List<Hotspot> HOTSPOTS = new ArrayList<>();
    /** Visible panel area of the last render; hotspots scrolled outside it are ignored. */
    private static int clipX, clipY, clipWidth, clipHeight;
    /** Offset of the edit-mode category block from the panel top (unscrolled), from the last render. */
    private static int categoryOffset;

    private static Hotspot hotspot(double mx, double my) {
        if (mx < clipX || mx >= clipX + clipWidth || my < clipY || my >= clipY + clipHeight) return null;
        for (int i = HOTSPOTS.size() - 1; i >= 0; i--) if (HOTSPOTS.get(i).contains(mx, my)) return HOTSPOTS.get(i);
        return null;
    }

    /** Item under the mouse (dish icon or ingredient cell), or null. */
    public static ResourceLocation hotspotAt(double mx, double my) {
        Hotspot spot = hotspot(mx, my);
        return spot == null || spot.kind() == HotKind.ACTION ? null : spot.item();
    }

    public static void clearHotspots() {
        HOTSPOTS.clear();
        TEXT_TIPS.clear();
        pinSpot = null;
        clipWidth = clipHeight = 0;
    }
    public static void dismissConfirmation() { signatureConfirm = false; }

    /** Full item tooltip for the dish icon or an ingredient cell; a tag cell adds its tag line. */
    public static void renderTooltip(GuiGraphics g, Font font, int mx, int my) {
        Hotspot spot = hotspot(mx, my);
        if (spot != null && spot == pinSpot && pinHint != null) { g.renderTooltip(font, pinHint, mx, my); return; }
        if (spot != null && TEXT_TIPS.containsKey(spot)) { g.renderComponentTooltip(font, TEXT_TIPS.get(spot), mx, my); return; }
        if (spot == null || spot.kind() == HotKind.ACTION || spot.item() == null) return;
        List<Component> extra = spot.tag() == null ? null : List.of(Component.translatable("cuisinejournal.detail.tag",
                "#" + spot.tag()).withStyle(s -> s.withColor(JournalTheme.ADJUSTED)));
        JournalText.renderItemTooltip(g, font, spot.item(), extra, mx, my);
    }

    /**
     * Handles a click: panel controls change state here ({@code HANDLED}); the dish icon asks
     * for the recipe viewer; an ingredient cell asks to open its tag, dish or ingredient entry.
     */
    public static Click click(double mx, double my, int button) {
        Hotspot spot = hotspot(mx, my);
        if (spot == null || button != 0 && button != 1) return null;
        switch (spot.kind()) {
            case ACTION -> {
                if (button != 0) return new Click(Click.Kind.HANDLED, null);
                spot.action().run();
                return new Click(Click.Kind.HANDLED, null);
            }
            case DISH -> { return new Click(button == 0 ? Click.Kind.RECIPES : Click.Kind.USES, spot.item()); }
            default -> {
                if (button == 1) return new Click(Click.Kind.USES, spot.item());
                if (spot.tag() != null) return new Click(Click.Kind.TAG, spot.tag());
                if (ClientJournalState.dish(spot.item()) != null) return new Click(Click.Kind.DISH, spot.item());
                return new Click(Click.Kind.INGREDIENT, spot.item());
            }
        }
    }

    // ------------------------------------------------------------ per-dish view state

    private static ResourceLocation stateDish;
    private static int shownStars = -1, recipeIndex;
    private static ResourceLocation openFlavor;
    private static boolean signatureConfirm;

    private static void syncState(ResourceLocation dish) {
        if (dish.equals(stateDish)) return;
        stateDish = dish;
        shownStars = -1;
        recipeIndex = 0;
        openFlavor = null;
        signatureConfirm=false;
    }

    // ------------------------------------------------------------ hover animation

    /** How far each icon is into its hover grow (0..1), keyed by its slot so repeats animate independently. */
    private static final java.util.Map<String, Float> HOVER = new java.util.HashMap<>();
    private static final float HOVER_SECONDS = 0.12F, HOVER_SCALE = 0.25F;
    private static long lastFrame;
    private static float frameStep;
    private static int mouseX = Integer.MIN_VALUE, mouseY = Integer.MIN_VALUE;

    private static void beginFrame(int mx, int my) {
        long now = System.nanoTime();
        float seconds = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1_000_000_000F);
        lastFrame = now;
        frameStep = seconds / HOVER_SECONDS;
        mouseX = mx;
        mouseY = my;
    }

    private static boolean hovered(int x, int y, int w, int h) {
        return mouseX >= clipX && mouseX < clipX + clipWidth && mouseY >= clipY && mouseY < clipY + clipHeight
                && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** Draws an item icon that grows slightly while hovered (ease-out, ~0.12s). */
    private static void growIcon(GuiGraphics g, String slot, ItemStack stack, int ix, int iy) {
        float t = HOVER.getOrDefault(slot, 0F);
        t = hovered(ix, iy, 16, 16) ? Math.min(1F, t + frameStep) : Math.max(0F, t - frameStep);
        if (t <= 0F) HOVER.remove(slot); else HOVER.put(slot, t);
        float eased = 1F - (1F - t) * (1F - t) * (1F - t);
        float scale = 1F + HOVER_SCALE * eased;
        g.pose().pushPose();
        // Lift above neighbours so the grown icon overlaps them instead of tucking under.
        g.pose().translate(ix + 8, iy + 8, t > 0F ? 50 : 0);
        g.pose().scale(scale, scale, 1F);
        g.renderFakeItem(stack, -8, -8);
        g.pose().popPose();
    }

    private static void action(int x, int y, int w, int h, Runnable run) {
        HOTSPOTS.add(new Hotspot(x, y, w, h, HotKind.ACTION, null, null, run));
    }

    // ------------------------------------------------------------ rendering

    public static void identity(GuiGraphics g, Font font, Packets.DishView dish,
                                Component name, int x, int y) {
        g.renderFakeItem(JournalText.stackOf(dish.target()), x, y);
        g.drawString(font, name, x + 20, y + 4, JournalTheme.TEXT_ACCENT, false);
        g.drawString(font, JournalText.modName(dish.sourceModId()),
                x, y + 22, JournalTheme.TEXT_DIM, false);
    }

    public static int render(GuiGraphics g, Font font, Packets.DishView dish,
                             int x, int y, int width, int height, int scroll) {
        return render(g, font, dish, x, y, width, height, scroll, false);
    }

    public static int render(GuiGraphics g, Font font, Packets.DishView dish,
                             int x, int y, int width, int height, int scroll, boolean editing) {
        return render(g, font, dish, x, y, width, height, scroll, editing, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    public static int render(GuiGraphics g, Font font, Packets.DishView dish,
                             int x, int y, int width, int height, int scroll, boolean editing,
                             int mouseX, int mouseY) {
        clearHotspots();
        syncState(dish.target());
        clipX = x + 1; clipY = y + 1; clipWidth = Math.max(0, width - 2); clipHeight = Math.max(0, height - 2);
        beginFrame(mouseX, mouseY);
        g.fill(x, y, x + width, y + height, 0xFF140F0A);
        JournalTheme.border(g, x, y, width, height, 0xFF3A2C1F);
        g.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        int left = x + 6, available = Math.max(1, width - 12), right = left + available;
        int top = y + 6 - scroll, cursor = top;
        boolean found = ClientJournalState.isDiscovered(dish.target());
        Packets.SyncDishDetail detail = ClientJournalState.detail(dish.target());
        boolean hide = !found && detail != null && detail.hideUndiscovered();

        // Header: icon (recipes / uses), gold name, status on the right (unchanged).
        HOTSPOTS.add(new Hotspot(left, cursor, 16, 16, HotKind.DISH, dish.target(), null, null));
        growIcon(g, "dish", JournalText.stackOf(dish.target()), left, cursor);
        Component status = Component.translatable("cuisinejournal.category." + (found ? "discovered" : "undiscovered"));
        int statusWidth = font.width(status) + (found ? 9 : 0);
        int statusX = right - statusWidth;
        int nameHeight = wrapped(g, font, JournalText.nameOf(dish.target()), left + 20, cursor + 4,
                Math.max(1, statusX - 6 - (left + 20)), JournalTheme.TEXT_ACCENT);
        if (found) {
            drawCheck(g, statusX, cursor + 5, JournalTheme.TEXT_GOOD);
            g.drawString(font, status, statusX + 9, cursor + 4, JournalTheme.TEXT_GOOD, false);
        } else g.drawString(font, status, statusX, cursor + 4, JournalTheme.TEXT_FAINT, false);
        cursor += Math.max(20, nameHeight + 6);
        // "模组 · 小类"
        Component sub = subName(dish);
        MutableComponent origin = JournalText.modName(dish.sourceModId()).copy();
        if (sub != null) origin.append(Component.literal(" · ").withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT))).append(sub);
        cursor += wrapped(g, font, origin, left, cursor, available, JournalTheme.TEXT_DIM) + 8;

        // Records (top, unchanged style).
        cursor = section(g, font, "records", left, cursor, available);
        Packets.RecordView record = ClientJournalState.record(dish.target());
        Component unknown = Component.literal("\u2014");
        cursor = recordRow(g, font, left, right, cursor, "cuisinejournal.category.detail.prepared_label",
                found && record != null ? Component.translatable("cuisinejournal.category.detail.times", record.prepared()) : unknown);
        cursor = recordRow(g, font, left, right, cursor, "cuisinejournal.category.detail.eaten_label",
                found && record != null ? Component.translatable("cuisinejournal.category.detail.times", record.eaten()) : unknown);
        cursor = recordRow(g, font, left, right, cursor, "cuisinejournal.category.detail.first_label",
                found && record != null && record.firstPreparedDay() != Long.MIN_VALUE
                        ? Component.translatable("cuisinejournal.category.detail.day", record.firstPreparedDay()) : unknown) + 6;

        // The category block is an editing tool; outside edit mode it would only duplicate the breadcrumbs.
        categoryOffset = cursor - top + 6;
        if (editing) cursor = categorySection(g, font, dish, left, cursor, available, true);

        if (hide) {
            g.drawString(font, Component.translatable("cuisinejournal.panel.undiscovered"), left, cursor, JournalTheme.TEXT_FAINT, false);
            cursor += 12;
        } else {
            if (found) cursor = stars(g, font, dish, left, right, cursor);
            if(found)cursor=signature(g,font,dish,left,right,cursor);
            if (shownStars < 0) shownStars = found ? ClientJournalState.rank(dish.target()).ordinal() : 0;
            cursor = attributes(g, font, dish, left, right, cursor);
            cursor = flavors(g, font, dish, left, right, cursor);
            cursor = recipes(g, font, dish, detail, left, right, cursor);
        }

        g.disableScissor();
        int maxScroll = Math.max(0, cursor - top + 12 - height);
        if (maxScroll > 0) {
            int trackX = x + width - 3;
            g.fill(trackX, y + 1, trackX + 2, y + height - 1, JournalTheme.BAR_TRACK);
            int thumb = Math.max(8, (height - 2) * (height - 2) / Math.max(height, cursor - top));
            int thumbY = y + 1 + (height - 2 - thumb) * scroll / maxScroll;
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumb, JournalTheme.TEXT_FAINT);
        }
        return maxScroll;
    }

    /** Selection is confirmed locally; qualification, fee and daily limit are authoritative on the server. */
    private static int signature(GuiGraphics g,Font font,Packets.DishView dish,int left,int right,int cursor){
        var features=ClientJournalState.journalFeatures();if(features==null)return cursor;
        boolean selected=dish.target().equals(features.signature());
        var record=ClientJournalState.record(dish.preparationTarget());
        boolean eligible=record!=null&&record.prepared()>0&&ClientJournalState.rank(dish.target()).isMastered();
        if(!selected&&!eligible)return cursor;
        String key=signatureConfirm?"cuisinejournal.signature.confirm":selected?"cuisinejournal.signature.clear":"cuisinejournal.signature.choose";
        Component label=Component.translatable(key);
        int w=Math.min(right-left,font.width(label)+12);boolean hover=hovered(left,cursor,w,12);
        g.fill(left,cursor,left+w,cursor+12,hover?0x443E6F42:0x222A2418);
        clippedText(g,font,label,left+4,cursor+2,w-8,selected?JournalTheme.TEXT_GOOD:JournalTheme.TEXT_ACCENT);
        action(left,cursor,w,12,()->{
            if(!signatureConfirm){signatureConfirm=true;return;}
            com.chi__you.cuisinejournal.network.Network.toServer(new com.chi__you.cuisinejournal.network.JournalFeaturePackets.Signature(selected?null:dish.target()));
            signatureConfirm=false;
        });
        cursor+=14;
        if(signatureConfirm){
            Component terms=features.chosen()?Component.translatable("cuisinejournal.signature.terms",features.cost()):Component.translatable("cuisinejournal.signature.first");
            cursor+=wrapped(g,font,terms,left,cursor,right-left,JournalTheme.TEXT_DIM);
        }
        if(selected){
            Component effect=Component.translatable(features.eligible()?"cuisinejournal.signature.bonus":"cuisinejournal.signature.inactive",
                    Math.round(features.bonus()*100));
            cursor+=wrapped(g,font,effect,left,cursor,right-left,JournalTheme.TEXT_DIM);
        }
        return cursor+4;
    }

    /** Stars and "小类 · 段位"; per-dish scope shows "points / next  段位" and a bar instead. */
    private static int stars(GuiGraphics g, Font font, Packets.DishView dish, int left, int right, int cursor) {
        cursor = section(g, font, "stars", left, cursor, right - left);
        MasteryRank rank = ClientJournalState.rank(dish.target());
        boolean mastered = rank.isMastered();
        int filled = rank.ordinal(), sx = left;
        for (int i = 0; i < MasteryRank.highestIndex(); i++) {
            // Same bitmap star as the item tooltip and the cooking side panel.
            g.drawString(font, com.chi__you.cuisinejournal.food.tooltip.TooltipText.star(i < filled ? JournalTheme.TEXT_ACCENT : 0x4A3E33),
                    sx, cursor, 0xFFFFFFFF, false);
            sx += 9;
        }
        Component rankName = Component.translatable(rank.translationKey());
        int rankWidth = font.width(rankName), rx = right - rankWidth;
        g.drawString(font, rankName, rx, cursor, mastered ? JournalTheme.TEXT_GOOD : JournalTheme.TEXT_DIM, false);
        boolean perDish = ClientJournalState.perDishMastery();
        long points = 0; int next = 0;
        if (perDish) {
            points = ClientJournalState.categoryMasteryPoints(dish.target());
            next = ClientJournalState.masteryCurve().thresholdFor(filled + 1);
            if (next > 0) {
                Component pts = Component.literal(JournalText.number(points) + " / " + JournalText.number(next));
                int pw = Math.round(font.width(pts) * 0.75F);
                small(g, font, pts, rx - 6 - pw, cursor + 2, JournalTheme.TEXT_FAINT);
            }
        } else {
            Component sub = subName(dish);
            if (sub != null) {
                Component dot = Component.literal(" · ");
                int dotX = rx - font.width(dot);
                g.drawString(font, dot, dotX, cursor, JournalTheme.TEXT_FAINT, false);
                int subWidth = Math.min(font.width(sub), dotX - sx - 4);
                if (subWidth > font.width("...")) clippedText(g, font, sub, dotX - subWidth, cursor, subWidth, JournalTheme.TEXT_DIM);
            }
        }
        cursor += 11;
        if (perDish) {
            float fill = next <= 0 ? 1F : Math.min(1F, (float) points / next);
            JournalTheme.bar(g, left, cursor, right - left, 4, fill, mastered);
            cursor += 8;
        }
        return cursor + 4;
    }

    /** Health / regen / duration bars as in the item tooltip, with a ★0–★5 switch; vanilla food shows hunger icons. */
    private static int attributes(GuiGraphics g, Font font, Packets.DishView dish, int left, int right, int cursor) {
        boolean vanilla = ClientJournalState.vanillaFood();
        boolean valheim = !vanilla && CuisineFoodClientState.active();
        var data = valheim ? CuisineFoodTooltip.info(dish.target(), Math.max(0, shownStars)) : null;
        if (!vanilla && data == null) return cursor;
        int titleY = cursor;
        var rules = ClientJournalState.rules();
        boolean switchable = valheim || rules != null && rules.bonusesEnabled();
        int switchWidth = switchable ? (MasteryRank.highestIndex() + 1) * 14 + 4 : 0;
        cursor = section(g, font, "attributes", left, cursor, right - left - switchWidth);
        if (switchable) starSwitch(g, font, dish, right, titleY);
        if (vanilla) {
            ItemStack stack = JournalText.stackOf(dish.target());
            var properties = stack.getFoodProperties(null);
            int nutrition = properties == null ? dish.nutrition() : properties.getNutrition();
            float modifier = properties == null ? dish.saturation() : properties.getSaturationModifier();
            if (rules != null && rules.bonusesEnabled() && shownStars > 0) {
                var value = CuisineVanillaBonuses.calculate(nutrition, modifier, new MealStamp(shownStars, false, 5, null, ""),
                        rules.vanillaSaturationMultiplierPerStar(), rules.vanillaMaxStarSaturation(),
                        rules.vanillaPerfectNutritionMultiplier());
                nutrition = value.nutrition(); modifier = value.saturationModifier();
            }
            if (com.chi__you.cuisinejournal.food.CuisineGameplayRules.nutritionDisplayEnabled()) {
                Component line = com.chi__you.cuisinejournal.client.tooltip.NutritionTooltip.format(nutrition, modifier);
                g.drawString(font, line, left, cursor + 1, 0xFFFFFFFF, true);
                cursor += 13;
            }
            // Same wording as the item tooltip: "滋养（03:00）", level and chance included.
            if (properties != null) for (var pair : properties.getEffects()) {
                var instance = pair.getFirst();
                if (instance == null || !Float.isFinite(pair.getSecond()) || pair.getSecond() <= 0) continue;
                Component line = com.chi__you.cuisinejournal.client.tooltip.NativeFoodEffectTooltip
                        .format(instance, Math.min(1F, pair.getSecond()));
                effectRow(g, font, instance.getEffect(), line, left, right, cursor);
                cursor += 11;
            }
            return cursor + 4;
        }
        // Valheim-style values: the tooltip's own bars, laid out in the panel's width.
        List<FoodTooltipComponent.StatBar> bars = CuisineFoodTooltip.statBars(data, false);
        int labelWidth = 0;
        for (var bar : bars) labelWidth = Math.max(labelWidth, font.width(bar.label()));
        int barX = left + labelWidth + 6, valueX = barX + 40 + 6;
        for (var bar : bars) {
            g.drawString(font, bar.label(), left, cursor + 1, 0xFFFFFFFF, false);
            ClientFoodTooltipComponent.drawBar(g, barX, cursor + 4, bar);
            clippedText(g, font, bar.value(), valueX, cursor + 1, right - valueX, 0xFFFFFFFF);
            cursor += 12;
        }
        cursor += 1;
        clippedText(g, font, CuisineFoodTooltip.slotSummary(data), left, cursor, right - left, 0xFFFFFFFF);
        cursor += 11;
        for (var entry : CuisineFoodTooltip.effectEntries(data)) {
            effectRow(g, font, entry.effect(), entry.text(), left, right, cursor);
            cursor += 11;
        }
        return cursor + 4;
    }

    /** "★0 … ★5" on the section title line; the player's own star is slightly brighter. */
    private static void starSwitch(GuiGraphics g, Font font, Packets.DishView dish, int right, int y) {
        int current = ClientJournalState.isDiscovered(dish.target()) ? ClientJournalState.rank(dish.target()).ordinal() : -1;
        int chipW = 13, cx = right - (MasteryRank.highestIndex() + 1) * (chipW + 1) + 1;
        for (int i = 0; i <= MasteryRank.highestIndex(); i++) {
            boolean on = i == shownStars, hover = hovered(cx, y - 1, chipW, 10);
            if (on) {
                g.fill(cx, y - 1, cx + chipW, y + 9, 0x24E8A93C);
                JournalTheme.border(g, cx, y - 1, chipW, 10, 0xA0E8A93C);
            } else if (hover) g.fill(cx, y - 1, cx + chipW, y + 9, 0x21FFE9C7);
            int colour = on ? JournalTheme.TEXT_ACCENT : hover ? JournalTheme.TEXT : i == current ? JournalTheme.TEXT_DIM : JournalTheme.TEXT_FAINT;
            Component label = Component.literal("\u2605" + i);
            int lw = Math.round(font.width(label) * 0.75F);
            small(g, font, label, cx + (chipW - lw + 1) / 2, y + 1, colour);
            int star = i;
            action(cx, y - 1, chipW, 10, () -> shownStars = star);
            cx += chipW + 1;
        }
    }

    /**
     * One effect line. Hovering lights the row, grows the icon and shows the effect's description;
     * clicking opens it in JEED (effect description page), ESC returns to the journal.
     */
    private static void effectRow(GuiGraphics g, Font font, MobEffect effect, Component text, int left, int right, int y) {
        int rowW = Math.min(right - left, 12 + font.width(text) + 2);
        boolean hover = hovered(left - 2, y - 1, rowW + 4, 11);
        if (hover) g.fill(left - 2, y - 1, left + rowW + 2, y + 10, 0x21FFE9C7);
        String slot = "effect/" + BuiltInRegistries.MOB_EFFECT.getKey(effect);
        float t = HOVER.getOrDefault(slot, 0F);
        t = hover ? Math.min(1F, t + frameStep) : Math.max(0F, t - frameStep);
        if (t <= 0F) HOVER.remove(slot); else HOVER.put(slot, t);
        float scale = 1F + HOVER_SCALE * (1F - (1F - t) * (1F - t) * (1F - t));
        var sprite = Minecraft.getInstance().getMobEffectTextures().get(effect);
        g.pose().pushPose();
        g.pose().translate(left + 4.5F, y + 4.5F, t > 0F ? 50 : 0);
        g.pose().scale(scale, scale, 1F);
        g.blit(-4, -4, 0, 9, 9, sprite);
        g.pose().popPose();
        clippedText(g, font, text, left + 12, y + 1, right - left - 12, 0xFFFFFFFF);
        List<Component> tip = new ArrayList<>();
        tip.add(effect.getDisplayName().copy().withStyle(effect.getCategory().getTooltipFormatting()));
        String key = effect.getDescriptionId() + ".description";
        if (net.minecraft.client.resources.language.I18n.exists(key))
            for (var line : font.getSplitter().splitLines(Component.translatable(key), 200, net.minecraft.network.chat.Style.EMPTY))
                tip.add(Component.literal(line.getString()).withStyle(s -> s.withColor(0xA79279)));
        boolean viewer = com.chi__you.cuisinejournal.client.compat.RecipeViewerBridge.canShowEffect();
        if (viewer) tip.add(Component.translatable("cuisinejournal.detail.effect_open").withStyle(s -> s.withColor(0x6E6153)));
        Hotspot spot = new Hotspot(left - 2, y - 1, rowW + 4, 11, HotKind.ACTION, null, null,
                viewer ? () -> com.chi__you.cuisinejournal.client.compat.RecipeViewerBridge.showEffect(effect) : () -> { });
        HOTSPOTS.add(spot);
        TEXT_TIPS.put(spot, tip);
    }

    /** Plain-text hover tooltips for ACTION hotspots (effect rows), keyed by this frame's hotspot. */
    private static final java.util.Map<Hotspot, List<Component>> TEXT_TIPS = new java.util.IdentityHashMap<>();

    /** Flavor names (click to show that flavor's stage table underneath). */
    private static int flavors(GuiGraphics g, Font font, Packets.DishView dish, int left, int right, int cursor) {
        List<ResourceLocation> flavors = FlavorClientState.dishFlavors(dish.target());
        if (flavors == null || flavors.isEmpty()) return cursor;
        flavors = flavors.stream().filter(FlavorUi::visible).toList();
        if (flavors.isEmpty()) return cursor;
        if (openFlavor != null && !flavors.contains(openFlavor)) openFlavor = null;
        cursor = section(g, font, "flavor", left, cursor, right - left);
        int fx = left;
        for (ResourceLocation id : flavors) {
            FlavorClientState.Entry entry = FlavorClientState.get(id);
            Component name = FlavorUi.name(id);
            int w = 12 + font.width(name) + 9;
            if (fx > left && fx + w > right) { fx = left; cursor += 13; }
            boolean open = id.equals(openFlavor), hover = hovered(fx - 2, cursor - 2, w + 3, 13);
            if (open) {
                g.fill(fx - 2, cursor - 2, fx + w + 1, cursor + 11, 0x1AE8A93C);
                JournalTheme.border(g, fx - 2, cursor - 2, w + 3, 13, 0x99E8A93C);
            } else if (hover) g.fill(fx - 2, cursor - 2, fx + w + 1, cursor + 11, 0x21FFE9C7);
            if (entry != null) FlavorUi.icon(g, entry.preset(), fx, cursor - 1, 10);
            else FlavorUi.leaf(g, fx, cursor - 1, 10);
            g.drawString(font, name, fx + 12, cursor, hover || open ? JournalTheme.TEXT : JournalTheme.DISCOVERY, false);
            small(g, font, Component.literal(open ? "\u25b4" : "\u25be"), fx + 13 + font.width(name), cursor + 2,
                    open ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT_FAINT);
            action(fx - 2, cursor - 2, w + 3, 13, () -> openFlavor = id.equals(openFlavor) ? null : id);
            fx += w + 6;
        }
        cursor += 13;
        FlavorClientState.Entry open = FlavorClientState.get(openFlavor);
        if (open != null) {
            FlavorStage active = FlavorStage.selectStage(open.preset().stages(), Math.max(0, shownStars));
            int textX = left + 5 + font.width("\u26050") + 6;
            List<List<net.minecraft.util.FormattedCharSequence>> bodies = new ArrayList<>();
            int boxHeight = 5;
            for (FlavorStage stage : open.preset().stages()) {
                // Breaks only between items, so a value never splits across lines.
                var lines = FlavorUi.wrapItems(font, FlavorUi.lines(stage), Math.max(1, right - textX));
                bodies.add(lines);
                boxHeight += Math.max(1, lines.size()) * 11;
            }
            boolean rule = open.preset().stages().size() > 1;
            if (rule) boxHeight += 9;
            // Background first so the stage text stays fully bright on top of it.
            g.fill(left, cursor, right, cursor + boxHeight, 0x40000000);
            g.fill(left, cursor, left + 1, cursor + boxHeight, 0x99E8A93C);
            int ty = cursor + 3;
            for (int i = 0; i < open.preset().stages().size(); i++) {
                FlavorStage stage = open.preset().stages().get(i);
                boolean here = stage == active;
                g.drawString(font, "\u2605" + stage.minStars(), left + 5, ty, here ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT_DIM, false);
                for (var line : bodies.get(i)) {
                    g.drawString(font, line, textX, ty, here ? JournalTheme.TEXT : JournalTheme.DISCOVERY, false);
                    ty += 11;
                }
                if (bodies.get(i).isEmpty()) ty += 11;
            }
            if (rule) small(g, font, Component.translatable("cuisinejournal.flavor.stage_rule"), left + 5, ty + 1, JournalTheme.TEXT_FAINT);
            cursor += boxHeight + 4;
        }
        return cursor + 2;
    }

    /** One recipe at a time; a cell per ingredient position, tags cycling through their items. */
    private static int recipes(GuiGraphics g, Font font, Packets.DishView dish, Packets.SyncDishDetail detail,
                               int left, int right, int cursor) {
        if (detail == null || detail.grouped().isEmpty()) return cursor;
        List<Packets.DetailRecipe> recipes = detail.grouped();
        recipeIndex = Math.floorMod(recipeIndex, recipes.size());
        Packets.DetailRecipe recipe = recipes.get(recipeIndex);
        cursor = section(g, font, "recipes", left, cursor, right - left);
        var features = ClientJournalState.journalFeatures();
        var tracked = features == null ? null : features.tracks().stream()
                .filter(t -> t.dish().equals(dish.target())).findFirst().orElse(null);
        int trackedIndex = -1;
        if (tracked != null) for (int i = 0; i < recipes.size(); i++)
            if (recipes.get(i).recipe().equals(tracked.recipe())) { trackedIndex = i; break; }
        boolean thisTracked = tracked != null && trackedIndex == recipeIndex;
        boolean full = tracked == null && features != null && features.tracks().size() >= features.trackLimit();
        // Station + pin + "n / m ‹ ›"
        int arrowsX = right;
        if (recipes.size() > 1) {
            arrowsX = right - 2 * 12 - 2;
            arrow(g, font, arrowsX, cursor - 1, "\u2039", () -> recipeIndex--);
            arrow(g, font, arrowsX + 14, cursor - 1, "\u203a", () -> recipeIndex++);
            Component count = Component.literal((recipeIndex + 1) + " / " + recipes.size());
            arrowsX -= font.width(count) + 4;
            g.drawString(font, count, arrowsX, cursor + 1, JournalTheme.TEXT_FAINT, false);
        }
        int sx = left;
        ItemStack station = JournalText.station(recipe.method());
        if (!station.isEmpty()) {
            g.pose().pushPose(); g.pose().translate(left, cursor - 1, 0); g.pose().scale(0.75F, 0.75F, 1F);
            g.renderFakeItem(station, 0, 0);
            g.pose().popPose();
            sx += 15;
        }
        // The pin sits right after the station name: click to track this recipe, click again to stop.
        int pinRoom = PIN_W + 6;
        if (recipe.method() != null) {
            Component method = JournalText.method(recipe.method());
            int max = arrowsX - 4 - sx - pinRoom;
            clippedText(g, font, method, sx, cursor + 1, max, JournalTheme.TEXT_DIM);
            sx += Math.min(font.width(method), Math.max(0, max)) + 5;
        }
        // Switching from another recipe of this dish keeps the portions already set.
        pin(g, sx, cursor - 1, dish.target(), recipe.recipe(), tracked == null ? 1 : tracked.target(), thisTracked, full);
        cursor += 14;
        int cell = 20, columns = Math.max(1, (right - left + 2) / cell);
        int index = 0;
        long tick = System.currentTimeMillis() / 1000L;
        for (Packets.DetailSlot slot : recipe.slots()) {
            if (slot.items().isEmpty()) continue;
            int cx = left + (index % columns) * cell, cy = cursor + (index / columns) * cell;
            ResourceLocation shown = slot.items().get((int) (tick % slot.items().size()));
            // No cell box: just the icon, growing while hovered.
            growIcon(g, "slot/" + recipeIndex + "/" + index, JournalText.stackOf(shown), cx + 1, cy + 1);
            if (slot.count() > 1) {
                String n = "\u00d7" + slot.count();
                g.pose().pushPose(); g.pose().translate(0, 0, 200);
                small(g, font, Component.literal(n), cx + 18 - Math.round(font.width(n) * 0.75F), cy + 12, 0xFFFFFFFF);
                g.pose().popPose();
            }
            HOTSPOTS.add(new Hotspot(cx, cy, 18, 18, HotKind.SLOT, shown, slot.tag(), null));
            index++;
        }
        int rows = (index + columns - 1) / columns;
        cursor += rows * cell + 4;
        return trackingLine(g, font, dish.target(), recipe.recipe(), tracked, trackedIndex, thisTracked, full,
                left, right, cursor);
    }

    private static final int PIN_W = 13, PIN_H = 11;
    /** X cap, H highlight, D flange underside, N needle. */
    private static final String[] PIN_ART = {
            ".XXXXX.",
            ".XHXXX.",
            "..XXX..",
            "XXXXXXX",
            ".DDDDD.",
            "...N...",
            "...N..."};
    /** The pin's hotspot this frame and its hover text ("追踪此配方" / "再次点击取消追踪" / "已达追踪上限"). */
    private static Hotspot pinSpot;
    private static Component pinHint;

    /** Pin toggle. Green when this recipe is tracked; dimmed and inert when the track list is full. */
    private static void pin(GuiGraphics g, int px, int py, ResourceLocation dish, ResourceLocation recipe, int target,
                            boolean on, boolean full) {
        boolean hover = !full && hovered(px, py, PIN_W, PIN_H);
        int edge = on ? (hover ? 0xFFB5DD8A : JournalTheme.TEXT_GOOD) : full ? 0xFF3A2C1F
                : hover ? JournalTheme.TEXT_DIM : JournalTheme.PANEL_BORDER;
        g.fill(px, py, px + PIN_W, py + PIN_H, on ? 0x268FBF5E : 0xFF1B140E);
        JournalTheme.border(g, px, py, PIN_W, PIN_H, edge);
        // Pushpin, 7×7 pixel art centred by arithmetic rather than by eye: the frame is odd on both
        // axes so the remainder is exactly zero (3px / 2px clear on every side).
        int artW = PIN_ART[0].length(), artH = PIN_ART.length;
        int ink = on ? JournalTheme.TEXT_GOOD : full ? 0xFF4A3A2A : hover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM;
        int shine = on ? 0xFFC8EBA0 : full ? 0xFF5A4A38 : 0xFFE6D7BE;
        int shade = on ? 0xFF50783A : full ? 0xFF2E2318 : 0xFF6E6153;
        int needle = full ? 0xFF3A2C1F : 0xFFA79279;
        int ox = px + (PIN_W - artW) / 2, oy = py + (PIN_H - artH) / 2;
        for (int row = 0; row < PIN_ART.length; row++) {
            String line = PIN_ART[row];
            for (int col = 0; col < line.length(); col++) {
                int colour = switch (line.charAt(col)) {
                    case 'X' -> ink; case 'H' -> shine; case 'D' -> shade; case 'N' -> needle; default -> 0;
                };
                if (colour != 0) g.fill(ox + col, oy + row, ox + col + 1, oy + row + 1, colour);
            }
        }
        pinHint = Component.translatable(on ? "cuisinejournal.tracking.pin_off" : full ? "cuisinejournal.tracking.full" : "cuisinejournal.tracking.pin_on");
        // Full: still a hotspot so the tooltip shows, but clicking does nothing.
        Runnable run = full ? () -> { } : () -> com.chi__you.cuisinejournal.network.Network.toServer(
                new com.chi__you.cuisinejournal.network.JournalFeaturePackets.Track(dish, on ? null : recipe, on ? 0 : target));
        pinSpot = new Hotspot(px, py, PIN_W, PIN_H, HotKind.ACTION, null, null, run);
        HOTSPOTS.add(pinSpot);
    }

    /**
     * Under the ingredients: "追踪中 − N 份 +" for the tracked recipe (−/+ resend the same recipe with
     * the new count), a hint when another recipe of this dish is tracked, or "已达追踪上限".
     */
    private static int trackingLine(GuiGraphics g, Font font, ResourceLocation dish, ResourceLocation recipe,
                                    com.chi__you.cuisinejournal.network.JournalFeaturePackets.Tracked tracked,
                                    int trackedIndex, boolean thisTracked, boolean full, int left, int right, int cursor) {
        if (thisTracked) {
            int target = tracked.target();
            g.fill(left, cursor, right, cursor + 16, 0xFF1B140E);
            JournalTheme.border(g, left, cursor, right - left, 16, JournalTheme.PANEL_BORDER);
            Component label = Component.translatable("cuisinejournal.tracking.active");
            g.drawString(font, label, left + 5, cursor + 4, JournalTheme.TEXT_GOOD, false);
            int x = left + 5 + font.width(label) + 6;
            arrow(g, font, x, cursor + 2, "-", () -> sendTrack(dish, recipe, Math.max(1, target - 1)));
            x += 16;
            Component amount = Component.translatable("cuisinejournal.tracking.portions", target);
            g.drawString(font, amount, x, cursor + 4, JournalTheme.TEXT, false);
            x += font.width(amount) + 4;
            arrow(g, font, x, cursor + 2, "+", () -> sendTrack(dish, recipe, Math.min(4096, target + 1)));
            return cursor + 20;
        }
        Component hint = tracked != null
                ? Component.translatable("cuisinejournal.tracking.other", trackedIndex >= 0 ? trackedIndex + 1 : "?")
                : full ? Component.translatable("cuisinejournal.tracking.full") : null;
        if (hint == null) return cursor;
        int h = 0;
        for (var line : font.split(hint, Math.max(1, (int) ((right - left) / 0.75F)))) {
            g.pose().pushPose(); g.pose().translate(left, cursor + h, 0); g.pose().scale(0.75F, 0.75F, 1F);
            g.drawString(font, line, 0, 0, JournalTheme.TEXT_FAINT, false);
            g.pose().popPose();
            h += 8;
        }
        return cursor + h + 4;
    }

    private static void sendTrack(ResourceLocation dish, ResourceLocation recipe, int target) {
        com.chi__you.cuisinejournal.network.Network.toServer(
                new com.chi__you.cuisinejournal.network.JournalFeaturePackets.Track(dish, recipe, target));
    }

    private static void arrow(GuiGraphics g, Font font, int x, int y, String glyph, Runnable run) {
        boolean hover = hovered(x, y, 12, 11);
        g.fill(x, y, x + 12, y + 11, hover ? 0xFF2A2018 : 0xFF1B140E);
        JournalTheme.border(g, x, y, 12, 11, hover ? JournalTheme.TEXT_DIM : JournalTheme.PANEL_BORDER);
        g.drawString(font, glyph, x + (12 - font.width(glyph) + 1) / 2, y + 2, hover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM, false);
        action(x, y, 12, 11, run);
    }

    // ------------------------------------------------------------ helpers

    /** The dish's subcategory name (null for mod fallback groups without a title). */
    private static Component subName(Packets.DishView dish) {
        if (dish.subcategory() == null) return null;
        for (Packets.CollectionView view : ClientJournalState.collections()) {
            if (!view.id().equals(dish.category())) continue;
            for (Packets.SubcategoryView sub : view.subcategories()) {
                if (!sub.id().equals(dish.subcategory())) continue;
                if (sub.id().getPath().equals("_mod")) return null;
                if (sub.titleKey() == null || sub.titleKey().isEmpty()) return null;
                return sub.literalTitle() ? Component.literal(sub.titleKey()) : Component.translatable(sub.titleKey());
            }
        }
        return null;
    }

    private static void small(GuiGraphics g, Font font, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75F, 0.75F, 1F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private static void clippedText(GuiGraphics g, Font font, Component text, int x, int y, int max, int colour) {
        if (max <= 0) return;
        if (font.width(text) <= max) { g.drawString(font, text, x, y, colour, false); return; }
        int dots = font.width("...");
        var clipped = font.substrByWidth(text, Math.max(0, max - dots));
        g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(clipped), x, y, colour, false);
        g.drawString(font, "...", x + font.width(clipped), y, JournalTheme.TEXT_FAINT, false);
    }

    private static int categorySection(GuiGraphics g, Font font, Packets.DishView dish,
                                       int left, int cursor, int available, boolean editing) {
        Component title = Component.translatable("cuisinejournal.edit.detail.category");
        g.drawString(font, title, left, cursor, JournalTheme.TEXT_FAINT, false);
        JournalTheme.divider(g, left + font.width(title) + 4, cursor + 4,
                Math.max(0, available - font.width(title) - 4));
        cursor += 13;
        var metadata = ClientJournalState.categoryEditor().categories();
        var root = metadata.stream().filter(v -> v.id().equals(dish.category()) && v.parent() == null).findFirst().orElse(null);
        var sub = metadata.stream().filter(v -> v.id().equals(dish.subcategory()) && v.parent() != null).findFirst().orElse(null);
        Component path = (root != null ? name(root, dish.category()) : viewName(dish.category(), null)).copy()
                .append(" › ").append(sub != null ? name(sub, dish.subcategory()) : viewName(dish.category(), dish.subcategory()));
        g.drawString(font, font.plainSubstrByWidth(path.getString(), Math.max(1, available - 12)), left, cursor, JournalTheme.TEXT_DIM, false);
        boolean manual = ClientJournalState.categoryEditor().manualItems().contains(dish.target());
        int markerX = left + available - 9;
        g.fill(markerX, cursor, markerX + 9, cursor + 9, 0xFF0E0A07);
        if (manual) g.fill(markerX + 3, cursor + 3, markerX + 6, cursor + 6, JournalTheme.ADJUSTED);
        else JournalTheme.border(g, markerX + 1, cursor + 1, 7, 7, JournalTheme.TEXT_FAINT);
        if (editing) {
            Component change = Component.translatable("cuisinejournal.edit.change");
            JournalTheme.value(g, font, change, left + available, cursor + 12, JournalTheme.TEXT_ACCENT);
        }
        return cursor + (editing ? 28 : 16);
    }

    private static Component name(com.chi__you.cuisinejournal.network.Packets.CategoryMetadata value,
                                  net.minecraft.resources.ResourceLocation fallback) {
        return value == null ? Component.literal(fallback.toString()) : value.literalTitle()
                ? Component.literal(value.titleKey()) : Component.translatable(value.titleKey());
    }

    /** Name from the synced collection views; covers implicit subcategories that have no editor metadata. */
    private static Component viewName(net.minecraft.resources.ResourceLocation category,
                                      net.minecraft.resources.ResourceLocation subcategory) {
        for (Packets.CollectionView view : ClientJournalState.collections()) {
            if (!view.id().equals(category)) continue;
            if (view.isModCategory()) return JournalText.modName(view.id().getNamespace(), view.titleKey());
            if (subcategory == null) return view.literalTitle() ? Component.literal(view.titleKey())
                    : Component.translatable(view.titleKey());
            for (Packets.SubcategoryView sub : view.subcategories()) {
                if (sub.id().equals(subcategory)) return sub.literalTitle() ? Component.literal(sub.titleKey())
                        : Component.translatable(sub.titleKey());
            }
        }
        return Component.literal((subcategory == null ? category : subcategory).toString());
    }

    /** Offset of the edit-mode category block from the panel top, as laid out by the last render. */
    public static int categoryY(Font font, Packets.DishView dish, int width) {
        return categoryOffset;
    }

    private static int section(GuiGraphics g, Font font, String key, int x, int y, int width) {
        Component title = Component.translatable("cuisinejournal.category.detail." + key);
        g.drawString(font, title, x, y, JournalTheme.TEXT_FAINT, false);
        int dividerX = x + font.width(title) + 4;
        if (dividerX < x + width) JournalTheme.divider(g, dividerX,
                y + (font.lineHeight - 1) / 2, x + width - dividerX);
        return y + font.lineHeight + 4;
    }

    private static int recordRow(GuiGraphics g, Font font, int x, int right, int y,
                                 String labelKey, Component value) {
        Component label = Component.translatable(labelKey);
        boolean unknown = value.getString().equals("—");
        if (font.width(label) + font.width(value) + 8 <= right - x) {
            JournalTheme.label(g, font, label, x, y, JournalTheme.TEXT_DIM);
            JournalTheme.value(g, font, value, right, y,
                    unknown ? JournalTheme.TEXT_FAINT : JournalTheme.TEXT);
            return y + 11;
        }
        int labelHeight = wrapped(g, font, label, x, y, right - x, JournalTheme.TEXT_DIM);
        int valueY = y + labelHeight;
        for (var line : font.split(value, Math.max(1, right - x))) {
            g.drawString(font, line, right - font.width(line), valueY,
                    unknown ? JournalTheme.TEXT_FAINT : JournalTheme.TEXT, false);
            valueY += 11;
        }
        return valueY;
    }

    public static int wrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        for (var line : font.split(text, Math.max(1, width))) {
            g.drawString(font, line, x, y, color, false);
            y += 11;
        }
        return font.split(text, Math.max(1, width)).size() * 11;
    }

    private static void dashed(GuiGraphics g, int x, int y, int width, int height) {
        for (int dx = 0; dx < width; dx += 4) {
            g.fill(x + dx, y, x + Math.min(width, dx + 2), y + 1, JournalTheme.DIVIDER);
            g.fill(x + dx, y + height - 1, x + Math.min(width, dx + 2), y + height, JournalTheme.DIVIDER);
        }
        for (int dy = 0; dy < height; dy += 4) {
            g.fill(x, y + dy, x + 1, y + Math.min(height, dy + 2), JournalTheme.DIVIDER);
            g.fill(x + width - 1, y + dy, x + width, y + Math.min(height, dy + 2), JournalTheme.DIVIDER);
        }
    }

    private static void drawCheck(GuiGraphics g, int x, int y, int color) {
        String[] pixels = {"......#", ".....##", "#...##.", "##.##..", ".###...", "..#...."};
        for (int row = 0; row < pixels.length; row++) {
            for (int col = 0; col < pixels[row].length(); col++) {
                if (pixels[row].charAt(col) == '#') {
                    g.fill(x + col, y + row, x + col + 1, y + row + 1, color);
                }
            }
        }
    }
}
