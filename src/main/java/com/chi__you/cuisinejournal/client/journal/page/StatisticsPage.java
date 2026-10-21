package com.chi__you.cuisinejournal.client.journal.page;

import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.client.journal.JournalText;
import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import com.chi__you.cuisinejournal.network.Packets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Personal statistics: headline numbers, personal records, streaks, cooking methods and
 * milestones / badges. One scrollable canvas; each block is laid out in content space and
 * shifted by the scroll offset.
 */
public final class StatisticsPage extends BasePage {
    private static final int GAP = 4;
    private static final int SECTION_GAP = 8;
    private static final int SCROLL_STEP = 14;
    private static final float SMALL = 0.75F;
    /** Icons sit on the card itself at 12px; they grow by a quarter while hovered. */
    private static final int ICON = 12;
    private static final float HOVER_GROW = 0.25F, HOVER_SECONDS = 0.12F;

    /** Hover target collected while drawing: an item (tooltip + recipe viewer) or text. */
    private record Hover(int x, int y, int w, int h, ResourceLocation item, List<Component> text) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Hover> hovers = new ArrayList<>();
    private final Map<String, Float> grow = new HashMap<>();
    private long lastFrame;
    private float frameStep;
    private int scroll, contentHeight, mouseX, mouseY;
    private boolean dragging;
    private record BadgeHit(int x,int y,int w,int h,ResourceLocation id){}
    private final List<BadgeHit> badgeHits=new ArrayList<>();

    @Override public String id() { return "statistics"; }
    @Override public Component title() { return Component.translatable("cuisinejournal.page.statistics"); }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("cuisinejournal.statistics." + key, args);
    }

    private int maxScroll() { return Math.max(0, contentHeight - height); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        hovers.clear();
        badgeHits.clear();
        mouseX = mx; mouseY = my;
        long now = System.nanoTime();
        frameStep = (lastFrame == 0 ? 0F : Math.min(0.1F, (now - lastFrame) / 1_000_000_000F)) / HOVER_SECONDS;
        lastFrame = now;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        int w = Math.max(1,width - 6);
        g.enableScissor(x, y, x + width, y + height);
        int top = y - scroll, cursor = top;
        Packets.SyncStatistics stats = ClientJournalState.statistics();

        // Title line: "个人统计  第 48 天 · 已烹饪 12 种料理"
        MutableComponent heading = Component.translatable("cuisinejournal.page.statistics").withStyle(s -> s.withBold(true));
        scaled(g, heading, x, cursor + 1, 10F / 9F, JournalTheme.TEXT_ACCENT);
        int cooked = (int) ClientJournalState.records().stream().filter(r -> r.prepared() > 0).count();
        Component sub = stats == null ? tr("cooked", bright(cooked))
                : tr("day_cooked", bright(stats.day()), bright(cooked));
        g.drawString(font(), sub, x + (int) Math.ceil(font().width(heading) * 10F / 9F) + 6, cursor + 2, JournalTheme.TEXT_DIM, false);
        cursor += 15;

        cursor = numberCards(g, cursor, w, stats);
        cursor = section(g, cursor + SECTION_GAP, w, tr("records"), null);
        cursor = records(g, cursor, w, stats);
        cursor = section(g, cursor + SECTION_GAP, w, tr("streaks"), tr("current_best"));
        cursor = streaks(g, cursor, w, stats);
        if (!ClientJournalState.methods().isEmpty()) {
            cursor = section(g, cursor + SECTION_GAP, w, tr("methods"), null);
            cursor = methods(g, cursor, w);
        }
        // Milestones live on the challenge page; only badges stay here.
        List<Packets.SyncStatistics.Achievement> badges = stats == null ? List.of()
                : stats.achievements().stream().filter(Packets.SyncStatistics.Achievement::badge).toList();
        if (!badges.isEmpty()) {
            long earned = badges.stream().filter(Packets.SyncStatistics.Achievement::earned).count();
            cursor = section(g, cursor + SECTION_GAP, w, tr("badges"), Component.literal(earned + " / " + badges.size()));
            cursor = badges(g, cursor, w, stats, badges);
        }
        contentHeight = cursor - top + 6;
        g.disableScissor();

        if (maxScroll() > 0) {
            int trackX = x + width - 3;
            g.fill(trackX, y, trackX + 3, y + height, JournalTheme.BAR_TRACK);
            int thumb = Math.max(12, height * height / Math.max(1, contentHeight));
            int thumbY = y + (height - thumb) * scroll / maxScroll();
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumb, JournalTheme.TEXT_FAINT);
        }
        if (mx >= x && mx < x + width && my >= y && my < y + height) {
            for (Hover hover : hovers) {
                if (!hover.contains(mx, my)) continue;
                if (hover.item() != null) JournalText.renderItemTooltip(g, font(), hover.item(), hover.text(), mx, my);
                else g.renderComponentTooltip(font(), hover.text(), mx, my);
                break;
            }
        }
    }

    // ------------------------------------------------------------------ blocks

    private int numberCards(GuiGraphics g, int cursor, int w, Packets.SyncStatistics stats) {
        long[] values = {ClientJournalState.mealsPrepared(), ClientJournalState.mealsEaten(),
                stats == null ? 0L : stats.ingredientUses(), ClientJournalState.portionsServed()};
        String[] labels = {"total_prepared", "total_eaten", "ingredient_uses", "total_served"};
        int cardW = (w - GAP * 3) / 4, cardH = 22;
        for (int i = 0; i < 4; i++) {
            int cx = x + i * (cardW + GAP);
            g.fill(cx, cursor, cx + cardW, cursor + cardH, JournalTheme.CARD);
            String number = JournalText.number(values[i]);
            float big = font().width(number) * 1.25F > cardW - 8 ? 1.0F : 1.25F;
            scaled(g, Component.literal(number), cx + 5, cursor + 3, big, values[i] > 0 ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT);
            scaled(g, tr(labels[i]), cx + 5, cursor + 15, SMALL, JournalTheme.TEXT_DIM);
        }
        return cursor + cardH;
    }

    private int section(GuiGraphics g, int cursor, int w, Component title, Component note) {
        g.drawString(font(), title, x, cursor, JournalTheme.TEXT_ACCENT, false);
        int lineX = x + font().width(title) + 5, lineEnd = x + w;
        if (note != null) {
            int noteW = (int) Math.ceil(font().width(note) * SMALL);
            scaled(g, note, x + w - noteW, cursor + 2, SMALL, JournalTheme.TEXT_FAINT);
            lineEnd -= noteW + 5;
        }
        if (lineEnd > lineX) JournalTheme.divider(g, lineX, cursor + 4, lineEnd - lineX);
        return cursor + 12;
    }

    private record Rec(String key, ItemStack icon, ResourceLocation item, Component value) {}

    private int records(GuiGraphics g, int cursor, int w, Packets.SyncStatistics stats) {
        List<Packets.RecordView> recs = ClientJournalState.records();
        Packets.RecordView mostPrepared = recs.stream().filter(r -> r.prepared() > 0)
                .max(Comparator.comparingLong(Packets.RecordView::prepared)).orElse(null);
        Packets.RecordView mostEaten = recs.stream().filter(r -> r.eaten() > 0)
                .max(Comparator.comparingLong(Packets.RecordView::eaten)).orElse(null);
        List<Rec> cells = new ArrayList<>();
        cells.add(new Rec("most_in_day", new ItemStack(Items.COOKIE), null,
                stats == null || stats.mostMealsInOneDay() <= 0 ? null
                        : amount(stats.mostMealsInOneDay(), "unit_dish", stats.mostMealsDay())));
        cells.add(new Rec("most_unique_in_day", new ItemStack(Items.BOWL), null,
                stats == null || stats.mostUniqueInOneDay() <= 0 ? null
                        : amount(stats.mostUniqueInOneDay(), "unit_kind", stats.mostUniqueDay())));
        cells.add(new Rec("most_prepared", mostPrepared == null ? new ItemStack(Items.COOKIE) : JournalText.stackOf(mostPrepared.target()),
                mostPrepared == null ? null : mostPrepared.target(),
                mostPrepared == null ? null : named(mostPrepared.target(), mostPrepared.prepared(), "unit_times")));
        cells.add(new Rec("most_eaten", mostEaten == null ? new ItemStack(Items.BREAD) : JournalText.stackOf(mostEaten.target()),
                mostEaten == null ? null : mostEaten.target(),
                mostEaten == null ? null : named(mostEaten.target(), mostEaten.eaten(), "unit_times")));
        boolean fastest = stats != null && stats.fastestMeal() != null && stats.fastestDays() >= 0;
        cells.add(new Rec("fastest_mastery", fastest ? JournalText.stackOf(stats.fastestMeal()) : new ItemStack(Items.NETHER_STAR),
                fastest ? stats.fastestMeal() : null,
                fastest ? named(stats.fastestMeal(), stats.fastestDays(), "unit_day") : null));
        cells.add(new Rec("feast_in_day", new ItemStack(Items.CAKE), null,
                stats == null || stats.mostFeastPortionsInOneDay() <= 0 ? null
                        : amount(stats.mostFeastPortionsInOneDay(), "unit_portion", Long.MIN_VALUE)));
        int colW = (w - GAP) / 2, cardH = 22;
        for (int i = 0; i < cells.size(); i++) {
            int cx = x + (i % 2) * (colW + GAP), cy = cursor + (i / 2) * (cardH + GAP);
            record(g, "rec/" + i, cells.get(i), cx, cy, colW, cardH);
        }
        int rows = (cells.size() + 1) / 2;
        return cursor + rows * (cardH + GAP) - GAP;
    }

    /** "46 道  第 33 天" — the number in gold, the day faint. */
    private static Component amount(long value, String unit, long day) {
        MutableComponent result = accent(value).append(tr(unit).withStyle(s -> s.withColor(JournalTheme.TEXT)));
        if (day != Long.MIN_VALUE && day >= 0)
            result.append(Component.literal("  ")).append(tr("on_day", day).withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT)));
        return result;
    }

    /** "曲奇 112 次" — dish name in normal text, the number in gold. */
    private static Component named(ResourceLocation dish, long value, String unit) {
        return JournalText.nameOf(dish).copy().withStyle(s -> s.withColor(JournalTheme.TEXT)).append(" ")
                .append(accent(value)).append(tr(unit).withStyle(s -> s.withColor(JournalTheme.TEXT)));
    }

    private void record(GuiGraphics g, String slot, Rec rec, int cx, int cy, int cw, int ch) {
        g.fill(cx, cy, cx + cw, cy + ch, JournalTheme.CARD);
        boolean done = rec.value() != null;
        int iconX = cx + 5, iconY = cy + (ch - ICON) / 2;
        growIcon(g, slot, rec.icon(), iconX, iconY, done ? 1F : 0.35F, rec.item());
        int textX = cx + 5 + ICON + 6, textW = cw - (textX - cx) - 4;
        scaled(g, Component.translatable("cuisinejournal.statistics." + rec.key()), textX, cy + 3, SMALL, JournalTheme.TEXT_DIM);
        Component shown = done ? rec.value() : tr("not_yet");
        if (font().width(shown) <= textW) {
            g.drawString(font(), shown, textX, cy + 11, done ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT, false);
        } else {
            g.drawString(font(), font().plainSubstrByWidth(shown.getString(), textW - font().width("…")) + "…",
                    textX, cy + 11, JournalTheme.TEXT, false);
            hovers.add(new Hover(textX, cy, textW, ch, null,
                    List.of(Component.translatable("cuisinejournal.statistics." + rec.key()), shown)));
        }
    }

    private int streaks(GuiGraphics g, int cursor, int w, Packets.SyncStatistics stats) {
        int[][] values = {
                {ClientJournalState.cookingStreak(), stats == null ? 0 : stats.longestCooking()},
                {ClientJournalState.varietyStreak(), stats == null ? 0 : stats.longestVariety()},
                {stats == null ? 0 : stats.discoveryStreak(), stats == null ? 0 : stats.longestDiscovery()}};
        String[] names = {"cooking_streak", "variety_streak", "discovery_streak"};
        String[] units = {"days", "dishes", "days"};
        int rowH = 12, blockH = values.length * rowH + 6;
        g.fill(x, cursor, x + w, cursor + blockH, JournalTheme.CARD);
        int nameW = 66, numW = 34;
        int barX = x + 6 + nameW, barW = w - 12 - nameW - numW * 2 - 10;
        for (int i = 0; i < values.length; i++) {
            int ry = cursor + 3 + i * rowH;
            int current = values[i][0], best = Math.max(values[i][1], current);
            g.drawString(font(), tr(names[i]), x + 6, ry + 2, JournalTheme.TEXT_DIM, false);
            if (barW > 10) thinBar(g, barX, ry + 4, barW, best <= 0 ? 0F : (float) current / best);
            JournalTheme.value(g, font(), tr(units[i], JournalText.number(current)), x + w - 6 - numW - 6, ry + 2, JournalTheme.TEXT);
            JournalTheme.value(g, font(), tr(units[i], JournalText.number(best)), x + w - 6, ry + 2, JournalTheme.TEXT_ACCENT);
        }
        return cursor + blockH;
    }

    private int methods(GuiGraphics g, int cursor, int w) {
        List<Packets.MethodView> list = ClientJournalState.methods().stream()
                .sorted(Comparator.comparingLong(Packets.MethodView::preparations).reversed()
                        .thenComparing(method -> method.method().toString())).toList();
        long max = Math.max(1L, list.get(0).preparations());
        int rowH = 16, gap = 2;
        int nameW = 50, countW = 26, metaW = 64, favW = ICON + 6;
        int index = 0;
        for (Packets.MethodView method : list) {
            g.fill(x, cursor, x + w, cursor + rowH, JournalTheme.CARD);
            int iconY = cursor + (rowH - ICON) / 2;
            drawIcon(g, stationIcon(method.method()), x + 4, iconY, ICON, 1F);
            int nx = x + 4 + ICON + 5;
            Component name = JournalText.method(method.method());
            g.drawString(font(), font().plainSubstrByWidth(name.getString(), nameW), nx, cursor + 4, JournalTheme.TEXT, false);
            if (font().width(name) > nameW) hovers.add(new Hover(nx, cursor, nameW, rowH, null, List.of(name)));
            int barX = nx + nameW + 4;
            int barW = w - (barX - x) - countW - metaW - favW - 10;
            if (barW > 10) thinBar(g, barX, cursor + 7, barW, (float) method.preparations() / max);
            int countRight = barX + Math.max(0, barW) + 4 + countW;
            JournalTheme.value(g, font(), Component.literal(JournalText.number(method.preparations())),
                    countRight, cursor + 4, JournalTheme.TEXT);
            MutableComponent meta = tr("method_kinds", method.uniqueMeals());
            if (method.masteredMeals() > 0) meta.append(tr("method_mastered",
                    Component.literal(String.valueOf(method.masteredMeals())).withStyle(s -> s.withColor(JournalTheme.TEXT_GOOD))));
            scaled(g, meta, countRight + 6, cursor + 5, SMALL, JournalTheme.TEXT_DIM);
            if (method.mostPrepared() != null) {
                int fx = x + w - ICON - 5;
                growIcon(g, "method/" + index, JournalText.stackOf(method.mostPrepared()), fx, iconY, 1F,
                        method.mostPrepared());
            }
            cursor += rowH + gap;
            index++;
        }
        return cursor - gap;
    }

    /** The workstation that stands for a cooking method; a cauldron when none matches. */
    private static ItemStack stationIcon(ResourceLocation method) {
        ItemStack stack = JournalText.station(method);
        return stack.isEmpty() ? new ItemStack(Items.CAULDRON) : stack;
    }

    private static final int BADGE_COLS = 8, BADGE_CELL_H = 30;

    /**
     * Badges as an 8-wide grid: icon over name. Owned ones in colour, missing ones washed out,
     * the worn one framed gold with a "佩戴" corner. Click an owned badge to wear it; click again
     * to take it off. Hover shows the name, how it is earned and its state.
     */
    private int badges(GuiGraphics g, int cursor, int w, Packets.SyncStatistics stats,
                       List<Packets.SyncStatistics.Achievement> list) {
        List<Packets.SyncStatistics.Achievement> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparing((Packets.SyncStatistics.Achievement a) -> !a.earned()));
        int cellW = (w - GAP * (BADGE_COLS - 1)) / BADGE_COLS;
        for (int i = 0; i < sorted.size(); i++) {
            var badge = sorted.get(i);
            int cx = x + (i % BADGE_COLS) * (cellW + GAP), cy = cursor + (i / BADGE_COLS) * (BADGE_CELL_H + GAP);
            boolean worn = badge.id().equals(stats.selectedBadge());
            boolean hover = mouseX >= cx && mouseX < cx + cellW && mouseY >= cy && mouseY < cy + BADGE_CELL_H
                    && mouseY >= y && mouseY < y + height;
            g.fill(cx, cy, cx + cellW, cy + BADGE_CELL_H, hover && badge.earned() ? JournalTheme.CARD_HOVER : JournalTheme.CARD);
            if (worn) JournalTheme.border(g, cx, cy, cellW, BADGE_CELL_H, JournalTheme.TEXT_ACCENT);
            ResourceLocation icon = badge.icon() == null ? ResourceLocation.parse("minecraft:gold_nugget") : badge.icon();
            growIcon(g, "badge/" + i, JournalText.stackOf(icon), cx + (cellW - ICON) / 2, cy + 4,
                    badge.earned() ? 1F : 0.35F, null, hover);
            Component name = badgeName(badge.id());
            int raw = (int) ((cellW - 4) / SMALL);
            String shown = font().width(name) <= raw ? name.getString() : font().plainSubstrByWidth(name.getString(), raw - font().width("…")) + "…";
            int nameW = (int) Math.ceil(font().width(shown) * SMALL);
            scaled(g, Component.literal(shown), cx + (cellW - nameW) / 2, cy + 20, SMALL,
                    badge.earned() ? JournalTheme.DISCOVERY : JournalTheme.TEXT_FAINT);
            if (worn) {
                Component tag = tr("worn");
                int tagW = (int) Math.ceil(font().width(tag) * 0.6F) + 3;
                g.fill(cx + cellW - tagW, cy, cx + cellW, cy + 7, JournalTheme.TEXT_ACCENT);
                g.pose().pushPose(); g.pose().translate(cx + cellW - tagW + 2, cy + 1, 0); g.pose().scale(0.6F, 0.6F, 1F);
                g.drawString(font(), tag, 0, 0, 0xFF1A1208, false);
                g.pose().popPose();
            }
            if (badge.earned()) badgeHits.add(new BadgeHit(cx, cy, cellW, BADGE_CELL_H, badge.id()));
            var tooltip = new ArrayList<Component>();
            tooltip.add(name.copy().withStyle(st -> st.withColor(JournalTheme.TEXT_ACCENT)));
            tooltip.add(condition(badge));
            if (badge.earned()) tooltip.add(Component.translatable(worn ? "cuisinejournal.badge.unequip" : "cuisinejournal.badge.equip")
                    .withStyle(st -> st.withColor(JournalTheme.TEXT_FAINT)));
            hovers.add(new Hover(cx, cy, cellW, BADGE_CELL_H, null, tooltip));
        }
        int rows = (sorted.size() + BADGE_COLS - 1) / BADGE_COLS;
        return cursor + rows * (BADGE_CELL_H + GAP) - GAP;
    }

    /** Display name of a badge id (also used by the challenge page). */
    public static Component badgeName(ResourceLocation id) {
        return label("badge", id);
    }

    /** Display name of a milestone or badge entry. */
    public static Component achievementLabel(Packets.SyncStatistics.Achievement achievement) {
        return achievementName(achievement);
    }

    private static Component label(String kind, ResourceLocation id) {
        String key = "cuisinejournal." + kind + "." + id.getNamespace() + "." + id.getPath();
        if (net.minecraft.locale.Language.getInstance().has(key)) return Component.translatable(key);
        return Component.literal(id.getPath().replace('_', ' '));
    }

    private static Component achievementName(Packets.SyncStatistics.Achievement achievement) {
        return label(achievement.badge() ? "badge" : "milestone", achievement.id());
    }

    private static Component condition(Packets.SyncStatistics.Achievement achievement) {
        MutableComponent how;
        if (achievement.badge()) {
            how = achievement.condition().isEmpty() ? tr(achievement.earned()?"badge_earned":"not_yet")
                    : tr("badge_from", Component.translatable(achievement.condition()));
        } else {
            how = tr("milestone_need", Component.translatable(achievement.condition()),
                    JournalText.number(achievement.amount()));
        }
        how.withStyle(s -> s.withColor(JournalTheme.TEXT_DIM));
        return how.append("  ").append(tr(achievement.earned() ? "achieved" : "not_yet").withStyle(s -> s.withColor(
                achievement.earned() ? JournalTheme.TEXT_GOOD : JournalTheme.TEXT_FAINT)));
    }

    // ------------------------------------------------------------------ drawing helpers

    /** 3px bar: dark track with a one-pixel border look and a gold fill, matching the journal's thin bars. */
    private static void thinBar(GuiGraphics g, int bx, int by, int bw, float fraction) {
        g.fill(bx, by, bx + bw, by + 3, JournalTheme.BAR_TRACK);
        int fill = Math.round(Math.max(0F, Math.min(1F, fraction)) * bw);
        for (int i = 0; i < fill; i++) {
            float t = fill <= 1 ? 0 : (float) i / (fill - 1);
            g.fill(bx + i, by, bx + i + 1, by + 3, blend(JournalTheme.BAR_FILL_START, JournalTheme.BAR_FILL_END, t));
        }
    }

    private static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gr = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }

    private void drawIcon(GuiGraphics g, ItemStack stack, int ix, int iy, int size, float alpha) {
        g.pose().pushPose();
        g.pose().translate(ix, iy, 0);
        g.pose().scale(size / 16F, size / 16F, 1F);
        g.renderFakeItem(stack, 0, 0);
        g.pose().popPose();
        if (alpha < 1F) {
            // Not achieved yet: wash the icon toward the card colour instead of drawing a box.
            g.pose().pushPose(); g.pose().translate(0, 0, 250);
            g.fill(ix, iy, ix + size, iy + size, ((int) ((1F - alpha) * 200) << 24) | 0x1A140F);
            g.pose().popPose();
        }
    }

    /**
     * An icon that grows a quarter while hovered, easing in and out over ~0.12 s (same feel as
     * the dish page). When it stands for an item, hovering shows its tooltip and clicks open
     * the recipe viewer.
     */
    private void growIcon(GuiGraphics g, String slot, ItemStack stack, int ix, int iy, float alpha, ResourceLocation item) {
        growIcon(g, slot, stack, ix, iy, alpha, item, false);
    }

    private void growIcon(GuiGraphics g, String slot, ItemStack stack, int ix, int iy, float alpha, ResourceLocation item,
                          boolean forceHover) {
        boolean hovered = forceHover || item != null && mouseX >= ix && mouseX < ix + ICON && mouseY >= iy && mouseY < iy + ICON
                && mouseY >= y && mouseY < y + height;
        float t = grow.getOrDefault(slot, 0F);
        t = hovered ? Math.min(1F, t + frameStep) : Math.max(0F, t - frameStep);
        if (t <= 0F) grow.remove(slot); else grow.put(slot, t);
        float eased = 1F - (1F - t) * (1F - t) * (1F - t);
        float scale = (1F + HOVER_GROW * eased) * ICON / 16F;
        g.pose().pushPose();
        g.pose().translate(ix + ICON / 2F, iy + ICON / 2F, t > 0F ? 50 : 0);
        g.pose().scale(scale, scale, 1F);
        g.renderFakeItem(stack, -8, -8);
        g.pose().popPose();
        if (alpha < 1F) {
            g.pose().pushPose(); g.pose().translate(0, 0, 250);
            g.fill(ix, iy, ix + ICON, iy + ICON, ((int) ((1F - alpha) * 200) << 24) | 0x1A140F);
            g.pose().popPose();
        }
        if (item != null) hovers.add(0, new Hover(ix, iy, ICON, ICON, item, List.of()));
    }

    private static MutableComponent bright(long value) {
        return Component.literal(JournalText.number(value)).withStyle(s -> s.withColor(JournalTheme.TEXT));
    }

    private static MutableComponent accent(long value) {
        return Component.literal(JournalText.number(value)).withStyle(s -> s.withColor(JournalTheme.TEXT_ACCENT));
    }

    private void scaled(GuiGraphics g, Component text, int tx, int ty, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font(), text, 0, 0, color, false);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ input

    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < x || mx >= x + width || my < y || my >= y + height) return false;
        int previous = scroll;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (delta * SCROLL_STEP)));
        com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.scroll(previous, scroll);
        return true;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if(button==0&&mx>=x&&mx<x+width&&my>=y&&my<y+height)for(var hit:badgeHits){
            if(mx>=hit.x()&&mx<hit.x()+hit.w()&&my>=hit.y()&&my<hit.y()+hit.h()){
                var stats=ClientJournalState.statistics();
                com.chi__you.cuisinejournal.network.Network.toServer(new Packets.SetPreference(Packets.SetPreference.Kind.BADGE,
                        stats!=null&&hit.id().equals(stats.selectedBadge())?null:hit.id()));
                com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.click();return true;
            }
        }
        if (button == 0 && maxScroll() > 0 && mx >= x + width - 4 && mx < x + width && my >= y && my < y + height) {
            dragging = true; drag(my); return true;
        }
        for (Hover hover : hovers) {
            if (hover.item() != null && hover.contains(mx, my)) {
                if (button == 0) com.chi__you.cuisinejournal.client.compat.RecipeViewerBridge.showRecipes(JournalText.stackOf(hover.item()));
                else if (button == 1) com.chi__you.cuisinejournal.client.compat.RecipeViewerBridge.showUses(JournalText.stackOf(hover.item()));
                return true;
            }
        }
        return false;
    }

    private void drag(double my) {
        int thumb = Math.max(12, height * height / Math.max(1, contentHeight));
        int travel = Math.max(1, height - thumb);
        scroll = (int) Math.max(0, Math.min(maxScroll(), (my - y - thumb / 2.0) / travel * maxScroll()));
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) { drag(my); return true; }
        return false;
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && dragging) { dragging = false; return true; }
        return false;
    }

    @Override public void onHidden() { super.onHidden(); dragging = false; }
}
