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
 * Challenge page (design: 百味纪挑战页设计稿.html), one scrolling column:
 * <ol>
 *   <li>two square cards side by side — today's daily challenge (left) and today's dish (right);</li>
 *   <li>challenges: in progress as full cards in two columns, completed ones as one-line cards
 *       (four shown, the rest behind "还有 N 个"), unavailable as a single line of names;</li>
 *   <li>milestones: one row each, a bar with raised threshold tabs and "current / next".</li>
 * </ol>
 * Reward items and badges are icons that grow on hover, show their tooltip, and open the recipe
 * viewer on click (items) — the same behaviour as the statistics page.
 */
public final class ChallengesPage extends BasePage {
    private static final int GAP = 4, SECTION_GAP = 8, SCROLL_STEP = 14, ICON = 12, DONE_SHOWN = 4;
    private static final int CARD_TALL = 44, MILESTONE_H = 20, DONE_H = 16;
    private static final float SMALL = 0.75F, HOVER_GROW = 0.25F, HOVER_SECONDS = 0.12F;

    private record Hover(int x, int y, int w, int h, ResourceLocation item, List<Component> text) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private record Click(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Hover> hovers = new ArrayList<>();
    private final List<Click> clicks = new ArrayList<>();
    private final Map<String, Float> grow = new HashMap<>();
    private long lastFrame;
    private float frameStep;
    private int scroll, contentHeight, mouseX, mouseY;
    private boolean dragging, doneOpen;

    @Override public String id() { return "challenges"; }
    @Override public Component title() { return Component.translatable("cuisinejournal.page.challenges"); }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("cuisinejournal.challenges." + key, args);
    }

    private int maxScroll() { return Math.max(0, contentHeight - height); }
    private boolean visible(int top, int h) { return top + h > y && top < y + height; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        hovers.clear(); clicks.clear();
        mouseX = mx; mouseY = my;
        long now = System.nanoTime();
        frameStep = (lastFrame == 0 ? 0F : Math.min(0.1F, (now - lastFrame) / 1_000_000_000F)) / HOVER_SECONDS;
        lastFrame = now;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        int w = Math.max(1,width - 6);
        g.enableScissor(x, y, x + width, y + height);
        int top = y - scroll, cursor = top;

        List<Packets.ChallengeView> all = ClientJournalState.challenges();
        Packets.ChallengeView daily = all.stream().filter(c -> "DAILY".equals(c.scope())).findFirst().orElse(null);
        cursor = squares(g, cursor, w, daily);

        List<Packets.ChallengeView> rest = all.stream().filter(c -> !"DAILY".equals(c.scope()) && !"WEEKLY".equals(c.scope())).toList();
        if (!rest.isEmpty()) {
            long done = rest.stream().filter(Packets.ChallengeView::completed).count();
            cursor = section(g, cursor + SECTION_GAP, w, tr("challenges"), Component.literal(done + " / " + rest.size()));
            cursor = challenges(g, cursor, w, rest);
        }
        Packets.SyncStatistics stats = ClientJournalState.statistics();
        List<Packets.SyncStatistics.Achievement> milestones = stats == null ? List.of()
                : stats.achievements().stream().filter(a -> !a.badge()).toList();
        if (!milestones.isEmpty()) {
            long earned = milestones.stream().filter(Packets.SyncStatistics.Achievement::earned).count();
            cursor = section(g, cursor + SECTION_GAP, w, tr("milestones"), Component.literal(earned + " / " + milestones.size()));
            cursor = milestones(g, cursor, w, milestones);
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
                else if (!hover.text().isEmpty()) g.renderComponentTooltip(font(), hover.text(), mx, my);
                break;
            }
        }
    }

    // ------------------------------------------------------------------ top squares

    private static final int SQUARE_H = 56;

    private int squares(GuiGraphics g, int cursor, int w, Packets.ChallengeView daily) {
        int colW = (w - GAP) / 2;
        dailyCard(g, x, cursor, colW, daily);
        todayCard(g, x + colW + GAP, cursor, w - colW - GAP);
        return cursor + SQUARE_H;
    }

    private void dailyCard(GuiGraphics g, int cx, int cy, int cw, Packets.ChallengeView daily) {
        boolean done = daily != null && daily.completed();
        g.fill(cx, cy, cx + cw, cy + SQUARE_H, JournalTheme.CARD);
        if (done) JournalTheme.border(g, cx, cy, cw, SQUARE_H, 0x668FBF5E);
        scaled(g, tr("daily"), cx + 6, cy + 5, SMALL, JournalTheme.TEXT_FAINT);
        if (daily == null) {
            scaled(g, tr("daily_none"), cx + 6, cy + 24, 1F, JournalTheme.TEXT_FAINT);
            return;
        }

        // Rewards at top-right (like challenge cards), on the same line as the label
        int rewardW = rewardsWidth(daily);
        rewards(g, "daily", cx + cw - 6 - rewardW, cy + 2, daily, done);

        var objective = daily.objectives().isEmpty() ? null : daily.objectives().get(0);
        ItemStack icon = objective == null ? new ItemStack(Items.BOOK) : objectiveIcon(objective);
        growIcon(g, "daily", icon, cx + 6, cy + 18, 1F, objectiveItem(objective), List.of());
        Component goal = objective == null ? title(daily) : goalWithAmount(objective);
        clipped(g, goal, cx + 6 + ICON + 6, cy + 20, cw - ICON - 18, done ? 0xFF86A862 : JournalTheme.TEXT);

        // Data band: everything below this hairline is progress, and it is centred in it.
        JournalTheme.divider(g, cx + 6, cy + 37, cw - 12);
        int progress = objective == null ? daily.progress() : objective.progress();
        int target = objective == null ? daily.goal() : objective.goal();
        // Unified count format
        MutableComponent count = Component.literal(String.valueOf(Math.min(progress, target))).withStyle(s -> s.withColor(done ? 0xFF86A862 : JournalTheme.TEXT))
                .append(Component.literal(" / " + target).withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT)));
        int countW = font().width(count);
        thinBar(g, cx + 6, cy + SQUARE_H - 11, cw - 18 - countW, target <= 0 ? 0F : (float) progress / target, done, done ? 0xFF6F9147 : 0);
        g.drawString(font(), count, cx + cw - 6 - countW, cy + SQUARE_H - 13, JournalTheme.TEXT, false);
    }

    private void todayCard(GuiGraphics g, int cx, int cy, int cw) {
        g.fill(cx, cy, cx + cw, cy + SQUARE_H, JournalTheme.CARD);
        Packets.SyncTodayDish today = ClientJournalState.todayDish();
        scaled(g, tr("today"), cx + 6, cy + 5, SMALL, JournalTheme.TEXT_FAINT);
        if (today == null || today.dish() == null) {
            scaled(g, tr(today == null ? "today_wait" : "today_none"), cx + 6, cy + 24, 1F, JournalTheme.TEXT_FAINT);
            return;
        }
        // The day number sits opposite the daily card's reward, as a small right-aligned note
        Component dayLabel = tr("today_day_short", today.day());
        int dayW = (int) Math.ceil(font().width(dayLabel) * SMALL);
        scaled(g, dayLabel, cx + cw - 6 - dayW, cy + 5, SMALL, JournalTheme.TEXT_FAINT);

        ResourceLocation dish = today.dish();
        // The 12px icon is centred on the whole name + mod block (16..32), not on the name line
        growIcon(g, "today", JournalText.stackOf(dish), cx + 6, cy + 18, 1F, dish, List.of());
        clicks.add(new Click(cx + 6, cy + 18, ICON, ICON, () -> openDish(dish)));
        clipped(g, JournalText.nameOf(dish), cx + 6 + ICON + 6, cy + 16, cw - ICON - 18, JournalTheme.TEXT_ACCENT);
        var view = ClientJournalState.dish(dish);
        if (view != null) scaled(g, JournalText.modName(view.sourceModId()), cx + 6 + ICON + 6, cy + 26, SMALL, JournalTheme.TEXT_DIM);

        JournalTheme.divider(g, cx + 6, cy + 37, cw - 12);
        // Data band, centred between the hairline and the card's bottom edge
        // (cy+38 .. cy+56, so a 6px line starts at cy+44): "星级 +1   完美概率 +20%"
        MutableComponent bonus = tr("today_stars").append(Component.literal(" +1").withStyle(s -> s.withColor(JournalTheme.TEXT_ACCENT)))
                .append("   ").append(tr("today_perfect")).append(Component.literal(" +20%").withStyle(s -> s.withColor(JournalTheme.TEXT_ACCENT)));
        scaled(g, bonus, cx + 6, cy + 44, SMALL, JournalTheme.TEXT_DIM);
        // A real second way into the dish detail, right-aligned in the band. It reacts on hover:
        // brighter text, a 1px nudge and a hairline underline, so it reads as clickable.
        Component more = tr("today_more");
        int moreW = (int) Math.ceil(font().width(more) * SMALL);
        int moreX = cx + cw - 6 - moreW;
        boolean moreHover = inside(moreX - 2, cy + 42, moreW + 6, 11);
        scaled(g, more, moreX + (moreHover ? 1 : 0), cy + 44, SMALL,
                moreHover ? JournalTheme.TEXT : JournalTheme.TEXT_ACCENT);
        if (moreHover) g.fill(moreX, cy + 51, moreX + moreW, cy + 52, JournalTheme.TEXT_DIM);
        clicks.add(new Click(cx + cw - 8 - moreW, cy + 42, moreW + 6, 10, () -> openDish(dish)));
    }

    private void openDish(ResourceLocation dish) {
        if (screen != null && screen.page("collections") instanceof CollectionsPage collections) {
            screen.jump(this, collections);
            if (!collections.openFood(dish)) screen.select(this);
        }
    }

    // ------------------------------------------------------------------ challenges

    private int challenges(GuiGraphics g, int cursor, int w, List<Packets.ChallengeView> list) {
        List<Packets.ChallengeView> active = list.stream().filter(c -> !c.completed() && !c.locked()).toList();
        List<Packets.ChallengeView> done = list.stream().filter(Packets.ChallengeView::completed).toList();
        List<Packets.ChallengeView> locked = list.stream().filter(c -> !c.completed() && c.locked()).toList();

        // In progress: full cards, two columns; each row as tall as its taller card.
        int colW = (w - GAP) / 2;
        for (int i = 0; i < active.size(); i += 2) {
            int h = Math.max(cardHeight(active.get(i)), i + 1 < active.size() ? cardHeight(active.get(i + 1)) : 0);
            card(g, "c" + i, x, cursor, colW, h, active.get(i));
            if (i + 1 < active.size()) card(g, "c" + (i + 1), x + colW + GAP, cursor, w - colW - GAP, h, active.get(i + 1));
            cursor += h + GAP;
        }
        // Completed: one-line cards, two columns, four shown.
        if (!done.isEmpty()) {
            cursor += 2;
            scaled(g, tr("done"), x, cursor, SMALL, JournalTheme.TEXT_FAINT);
            cursor += 9;
            int shown = doneOpen ? done.size() : Math.min(DONE_SHOWN, done.size());
            for (int i = 0; i < shown; i++) {
                int cx = x + (i % 2) * (colW + GAP), cy = cursor + (i / 2) * (DONE_H + GAP);
                doneCard(g, "d" + i, cx, cy, i % 2 == 0 ? colW : w - colW - GAP, done.get(i));
            }
            cursor += (shown + 1) / 2 * (DONE_H + GAP);
            if (done.size() > DONE_SHOWN) {
                Component more = doneOpen ? tr("done_less") : tr("done_more", done.size() - DONE_SHOWN);
                int mw = font().width(more);
                boolean hover = inside(x, cursor, mw + 4, 10);
                scaled(g, more, x, cursor + 1, SMALL, hover ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT_DIM);
                clicks.add(new Click(x, cursor, mw + 4, 10, () -> doneOpen = !doneOpen));
                cursor += 12;
            }
        }
        // Unavailable: a single line of names.
        if (!locked.isEmpty()) {
            cursor += 2;
            MutableComponent line = tr("locked").append("  ");
            for (int i = 0; i < locked.size(); i++) {
                if (i > 0) line.append(Component.literal(" · ").withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT)));
                line.append(title(locked.get(i)).copy().withStyle(s -> s.withColor(JournalTheme.TEXT_DIM)));
            }
            for (var row : font().split(line, (int) (w / SMALL))) {
                g.pose().pushPose(); g.pose().translate(x, cursor, 0); g.pose().scale(SMALL, SMALL, 1F);
                g.drawString(font(), row, 0, 0, JournalTheme.TEXT_FAINT, false);
                g.pose().popPose();
                cursor += 9;
            }
        }
        return cursor - GAP;
    }

    private int cardHeight(Packets.ChallengeView c) {
        // Title line + description line + objective rows (max 1 per objective)
        return CARD_TALL + Math.max(0,c.objectives().size()-1)*10;
    }

    private void card(GuiGraphics g, String slot, int cx, int cy, int cw, int ch, Packets.ChallengeView c) {
        g.fill(cx, cy, cx + cw, cy + ch, JournalTheme.CARD);
        // 2px gold edge on the left for in-progress
        g.fill(cx, cy, cx + 2, cy + ch, 0x8CE8A93C);

        int rewardW = rewardsWidth(c);
        Component name = title(c);
        int nameMax = cw - 14 - rewardW - 6;
        boolean daySpan = c.objectives().stream().anyMatch(o -> o.type().equals("UNIQUE_RECIPES_IN_ONE_DAY"));
        Component tag = tr("same_day");
        int tagW = daySpan ? (int) Math.ceil(font().width(tag) * SMALL) + 6 : 0;
        clipped(g, name, cx + 8, cy + 6, nameMax - tagW, JournalTheme.TEXT);
        if (daySpan) {
            int tx = cx + 8 + Math.min(font().width(name), nameMax - tagW) + 4;
            int boxW = tagW - 2;
            JournalTheme.border(g, tx, cy + 5, boxW, 10, JournalTheme.TEXT_DIM);
            // Centre the tag by its glyph ink: font.width() counts the 1px gap after the
            // last glyph, so centring by it alone shifts the text ~0.6px left.
            float inkW = font().width(tag) * SMALL - SMALL;
            scaledAt(g, tag, tx + (boxW - inkW) / 2F, cy + 7, SMALL, JournalTheme.TEXT_DIM);
            hovers.add(new Hover(tx, cy + 5, tagW, 10, null, List.of(tr("same_day_hint"))));
        }
        rewards(g, slot, cx + cw - 6 - rewardW, cy + 3, c, false);

        // Description line (always shown), one step quieter than the objective row
        String description = c.descriptionKey();
        if (description != null && !description.isEmpty()) {
            Component desc = Component.translatable(description);
            clippedScaled(g, desc, cx + 8, cy + 20, (int)((cw - 16) / SMALL), JournalTheme.TEXT_DIM);
            // Still show full text on hover over the name + desc area
            hovers.add(new Hover(cx + 8, cy + 6, nameMax, 22, null, List.of(name, desc.copy().withStyle(s -> s.withColor(JournalTheme.TEXT_DIM)))));
        }

        // Objectives starting at y+32: 6px below the description, 6px above the card edge
        int oy = cy + 32;
        if (c.objectives().isEmpty()) objectiveRow(g, cx + 8, oy, cw - 16, title(c), c.progress(), c.goal());
        for (var o : c.objectives()) { objectiveRow(g, cx + 8, oy, cw - 16, objectiveText(o), o.progress(), o.goal()); oy += 10; }
    }

    private void objectiveRow(GuiGraphics g, int ox, int oy, int ow, Component text, int progress, int goal) {
        boolean ok = goal > 0 && progress >= goal;
        // Unified count format: "current / goal", current bright, " / goal" faint
        MutableComponent count = Component.literal(String.valueOf(progress)).withStyle(s -> s.withColor(JournalTheme.TEXT))
                .append(Component.literal(" / " + goal).withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT)));
        int countW = (int) Math.ceil(font().width(count) * SMALL);
        int textW = Math.min((int) (font().width(text) * SMALL), ow - countW - 40);
        MutableComponent shown = ok ? Component.literal("✔ ").append(text) : text.copy();
        clippedScaled(g, shown, ox, oy + 1, (int)(textW / SMALL), ok ? JournalTheme.TEXT_GOOD : JournalTheme.DISCOVERY);
        int bx = ox + textW + 6, bw = ox + ow - countW - 5 - bx;
        thinBar(g, bx, oy + 3, bw, goal <= 0 ? 0F : (float) progress / goal, ok);
        scaled(g, count, ox + ow - countW, oy + 1, SMALL, JournalTheme.TEXT);
    }

    private void doneCard(GuiGraphics g, String slot, int cx, int cy, int cw, Packets.ChallengeView c) {
        g.fill(cx, cy, cx + cw, cy + DONE_H, 0x1A6F9147); // muted green fill
        int rewardW = rewardsWidth(c);
        MutableComponent text = Component.literal("✔ ").withStyle(s -> s.withColor(0xFF86A862)).append(title(c));
        if (!c.objectives().isEmpty()) text.append(Component.literal(" · ").withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT)))
                .append(objectiveText(c.objectives().get(0)).copy().withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT)));
        clippedScaled(g, text, cx + 5, cy + 5, cw - 10 - rewardW - 4, JournalTheme.TEXT_DIM);
        rewards(g, slot, cx + cw - 5 - rewardW, cy + 2, c, true);
        String description = c.descriptionKey();
        if (description != null && !description.isEmpty())
            hovers.add(new Hover(cx, cy, cw - rewardW - 8, DONE_H, null, List.of(title(c),
                    Component.translatable(description).withStyle(s -> s.withColor(JournalTheme.TEXT_DIM)))));
    }

    // ------------------------------------------------------------------ rewards

    private record Reward(ResourceLocation item, int count) {}

    private static List<Reward> items(Packets.ChallengeView c) {
        List<Reward> result = new ArrayList<>();
        for (String raw : c.itemRewards()) {
            String[] parts = raw.trim().split("\\s+");
            ResourceLocation id = parts.length == 0 ? null : ResourceLocation.tryParse(parts[0]);
            if (id == null) continue;
            int count = 1;
            if (parts.length > 1) try { count = Math.max(1, Integer.parseInt(parts[1])); } catch (NumberFormatException ignored) { }
            result.add(new Reward(id, count));
        }
        return result;
    }

    private int rewardsWidth(Packets.ChallengeView c) {
        int w = 0;
        if (c.cookingXp() > 0) w += (int) Math.ceil(font().width(xpText(c, 0, 0)) * SMALL) + 3;
        w += items(c).size() * (ICON + 2);
        if (c.badge() != null) w += ICON + 2;
        return Math.max(0, w - 2);
    }

    /** "+40 经验": the amount in the accent colour, the unit one step quieter. */
    private static MutableComponent xpText(Packets.ChallengeView c, int numberColour, int unitColour) {
        return Component.literal("+" + c.cookingXp()).withStyle(s -> s.withColor(numberColour))
                .append(tr("xp_unit").withStyle(s -> s.withColor(unitColour)));
    }

    /** "+40 经验" then reward items and the badge as bare icons that grow on hover. */
    private void rewards(GuiGraphics g, String slot, int rx, int ry, Packets.ChallengeView c, boolean done) {
        int cx = rx;
        if (c.cookingXp() > 0) {
            Component xp = xpText(c, done ? 0xFF86A862 : JournalTheme.TEXT_ACCENT,
                    done ? 0xFF86A862 : JournalTheme.TEXT_DIM);
            scaled(g, xp, cx, ry + 3, SMALL, JournalTheme.TEXT_ACCENT);
            cx += (int) Math.ceil(font().width(xp) * SMALL) + 3;
        }
        int index = 0;
        for (Reward reward : items(c)) {
            growIcon(g, slot + "/i" + index++, JournalText.stackOf(reward.item()), cx, ry, 1F, reward.item(),
                    reward.count() > 1 ? List.of(tr("reward_count", reward.count()).withStyle(s -> s.withColor(JournalTheme.TEXT_DIM))) : List.of());
            if (done) wash(g, cx, ry, ICON, 0.4F);
            if (reward.count() > 1) {
                String n = String.valueOf(reward.count());
                g.pose().pushPose(); g.pose().translate(0, 0, 260);
                scaled(g, Component.literal(n), cx + ICON - (int) Math.ceil(font().width(n) * SMALL) + 1, ry + ICON - 5, SMALL, JournalTheme.TEXT);
                g.pose().popPose();
            }
            cx += ICON + 2;
        }
        if (c.badge() != null) {
            badgeIcon(g, slot + "/b", c.badge(), cx, ry, !done);
            if (done) wash(g, cx, ry, ICON, 0.4F);
        }
    }

    /** A badge icon: grows on hover and shows the badge's name, how it is earned and whether it is owned. */
    private void badgeIcon(GuiGraphics g, String slot, ResourceLocation badge, int bx, int by, boolean bright) {
        Packets.SyncStatistics.Achievement info = badgeInfo(badge);
        ResourceLocation icon = info == null || info.icon() == null ? ResourceLocation.parse("minecraft:gold_nugget") : info.icon();
        boolean owned = info != null && info.earned();
        List<Component> text = new ArrayList<>();
        text.add(tr("badge", StatisticsPage.badgeName(badge)).withStyle(s -> s.withColor(JournalTheme.TEXT_ACCENT)));
        text.add((owned ? tr("badge_owned") : tr("badge_missing")).withStyle(s -> s.withColor(owned ? JournalTheme.TEXT_GOOD : JournalTheme.TEXT_FAINT)));
        growIcon(g, slot, JournalText.stackOf(icon), bx, by, bright || owned ? 1F : 0.4F, null, text);
    }

    private static Packets.SyncStatistics.Achievement badgeInfo(ResourceLocation badge) {
        Packets.SyncStatistics stats = ClientJournalState.statistics();
        if (stats == null || badge == null) return null;
        for (var a : stats.achievements()) if (a.badge() && a.id().equals(badge)) return a;
        return null;
    }

    // ------------------------------------------------------------------ milestones

    /**
     * One row per counted stat: the name, a bar whose thresholds are raised tabs (equally spaced,
     * filled piecewise between them), and "current / next". A tab with a badge carries its icon.
     * Row height 20px: name and count vertically centered at y+8, bar at y+14, tabs above the bar.
     */
    private int milestones(GuiGraphics g, int cursor, int w, List<Packets.SyncStatistics.Achievement> list) {
        Map<String, List<Packets.SyncStatistics.Achievement>> groups = new java.util.LinkedHashMap<>();
        // Same counted stat = one row. Per-workstation milestones share a type, so they are split
        // by their name with the number removed ("切制板 50 份" / "切制板 200 份" → one row).
        for (var a : list) {
            String key = a.condition();
            if (key.endsWith("method_preparations"))
                key += "|" + StatisticsPage.achievementLabel(a).getString().replaceAll("[0-9,，.]+", "#");
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
        }

        // Compute fixed column widths: max(name widths) capped at 90, max(count widths)
        int nameW = 0, countCol = 0;
        for (var group : groups.values()) {
            group.sort(Comparator.comparingLong(Packets.SyncStatistics.Achievement::amount));
            var first = group.get(0);
            Component name = first.condition().endsWith("method_preparations") ? StatisticsPage.achievementLabel(first)
                    : Component.translatable(first.condition());
            nameW = Math.max(nameW, font().width(name));

            long current = group.stream().mapToLong(Packets.SyncStatistics.Achievement::progress).max().orElse(0);
            long next = group.stream().filter(s -> !s.earned()).mapToLong(Packets.SyncStatistics.Achievement::amount).min()
                    .orElse(group.get(group.size() - 1).amount());
            boolean all = group.stream().allMatch(Packets.SyncStatistics.Achievement::earned);
            MutableComponent count = all
                    ? Component.literal("✔ " + JournalText.number(current)).withStyle(s -> s.withColor(0xFF86A862))
                    : Component.literal(JournalText.number(current)).withStyle(s -> s.withColor(JournalTheme.TEXT))
                    .append(Component.literal(" / " + JournalText.number(next)).withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT)));
            countCol = Math.max(countCol, font().width(count));
        }
        nameW = Math.min(90, nameW);

        int row = 0;
        for (var group : groups.values()) {
            milestoneRow(g, "m" + row++, x, cursor, w, group, nameW, countCol);
            cursor += MILESTONE_H + GAP;
        }
        return cursor - GAP;
    }

    private void milestoneRow(GuiGraphics g, String slot, int rx, int ry, int rw, List<Packets.SyncStatistics.Achievement> steps, int nameW, int countCol) {
        g.fill(rx, ry, rx + rw, ry + MILESTONE_H, JournalTheme.CARD);
        var first = steps.get(0);
        long current = steps.stream().mapToLong(Packets.SyncStatistics.Achievement::progress).max().orElse(0);
        boolean all = steps.stream().allMatch(Packets.SyncStatistics.Achievement::earned);

        Component name = first.condition().endsWith("method_preparations") ? StatisticsPage.achievementLabel(first)
                : Component.translatable(first.condition());
        clipped(g, name, rx + 6, ry + 7, nameW, all ? 0xFF9FA09F : JournalTheme.TEXT_DIM);

        long next = steps.stream().filter(s -> !s.earned()).mapToLong(Packets.SyncStatistics.Achievement::amount).min()
                .orElse(steps.get(steps.size() - 1).amount());
        MutableComponent count = all
            ? Component.literal("✔ " + JournalText.number(current)).withStyle(s -> s.withColor(0xFF86A862))
            : Component.literal(JournalText.number(current)).withStyle(s -> s.withColor(JournalTheme.TEXT))
                .append(Component.literal(" / " + JournalText.number(next)).withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT)));
        int countW = font().width(count);
        // Right-align count in the fixed countCol
        g.drawString(font(), count, rx + rw - 6 - countCol + (countCol - countW), ry + 7, JournalTheme.TEXT, false);

        // The bar stops 12px short of the count column so a threshold number centred on the
        // last tick cannot run into the count.
        int barX = rx + 6 + nameW + 8, barW = rw - (barX - rx) - countCol - 18, barY = ry + 14;
        if (barW < 20) return;
        int n = steps.size();
        // Equally spaced ticks; the fill is interpolated inside the segment the value falls in.
        float fill = 0F;
        long previous = 0;
        for (int i = 0; i < n; i++) {
            long threshold = steps.get(i).amount();
            float at = (float) (i + 1) / n, from = (float) i / n;
            if (current >= threshold) { fill = at; previous = threshold; continue; }
            fill = from + (at - from) * Math.max(0F, (float) (current - previous) / Math.max(1, threshold - previous));
            break;
        }
        thinBar(g, barX, barY, barW, fill, all, all ? 0xFF6F9147 : 0);
        for (int i = 0; i < n; i++) {
            var step = steps.get(i);
            int tabX = barX + Math.round(barW * (float) (i + 1) / n);
            tab(g, slot + "/" + i, tabX, barY, step, all);
        }
    }

    /**
     * A scale mark standing on the bar at {@code cx}: the threshold number centred on its tick,
     * a 1px underline under the digits, and a 1px stem down to the bar. The badge icon (when this
     * step awards one) hangs to the left of the digits, so it never pushes them off centre.
     */
    private void tab(GuiGraphics g, String slot, int cx, int barY, Packets.SyncStatistics.Achievement step, boolean allReached) {
        Component label = Component.literal(JournalText.number(step.amount()));
        int textW = (int) Math.ceil(font().width(label) * SMALL);
        boolean badge = step.reward() != null;
        boolean earned = step.earned();
        int numX = cx - textW / 2;
        int hx = numX - (badge ? 12 : 2), hw = textW + (badge ? 14 : 4);
        // The whole mark is one hover target: hovering the digits grows the badge too.
        boolean hovered = inside(hx, barY - 12, hw, 12);
        int ink = earned ? (allReached ? 0xFFB89452 : JournalTheme.TEXT_ACCENT) : JournalTheme.TEXT_FAINT;
        int rule = earned ? (allReached ? 0x806B5630 : 0xB3E8A93C) : 0xC04A3A2A;
        int stem = earned ? (allReached ? 0x806B5630 : 0xD9E8A93C) : 0xC04A3A2A;
        if (badge) {
            Packets.SyncStatistics.Achievement info = badgeInfo(step.reward());
            ResourceLocation icon = info == null || info.icon() == null ? ResourceLocation.parse("minecraft:gold_nugget") : info.icon();
            smallBadge(g, slot, JournalText.stackOf(icon), numX - 10, barY - 10, hovered);
        }
        scaled(g, label, numX, barY - 9, SMALL, ink);
        // GuiGraphics.fill takes two corners, not (x, y, width, height): a reversed
        // rectangle still rasterises and would paint a large block.
        g.fill(numX, barY - 2, numX + textW, barY - 1, rule);
        g.fill(cx, barY - 2, cx + 1, barY, stem); // stem down to the bar
        List<Component> text = new ArrayList<>();
        text.add(StatisticsPage.achievementLabel(step));
        text.add((earned ? tr("milestone_done") : tr("milestone_need", JournalText.number(step.amount())))
                .withStyle(s -> s.withColor(earned ? JournalTheme.TEXT_GOOD : JournalTheme.TEXT_FAINT)));
        if (badge) text.add(tr("badge", StatisticsPage.badgeName(step.reward())).withStyle(s -> s.withColor(JournalTheme.TEXT_ACCENT)));
        hovers.add(new Hover(hx, barY - 12, hw, 12, null, text));
    }

    /**
     * 7px badge icon inside a milestone tab. It grows when the whole mark is hovered, and it is
     * drawn at full brightness: the old 40% black overlay covered the sprite's transparent margin
     * and read as a black frame around the icon. Earned state is carried by the digits and the
     * underline instead.
     */
    private void smallBadge(GuiGraphics g, String slot, ItemStack stack, int ix, int iy, boolean hovered) {
        int size = 7;
        float t = step(slot, hovered);
        float eased = 1F - (1F - t) * (1F - t) * (1F - t);
        float scale = (1F + HOVER_GROW * eased) * size / 16F;
        g.pose().pushPose();
        g.pose().translate(ix + size / 2F, iy + size / 2F, t > 0F ? 50 : 0);
        g.pose().scale(scale, scale, 1F);
        g.renderFakeItem(stack, -8, -8);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ wording

    private static Component title(Packets.ChallengeView c) {
        return Component.translatable(c.titleKey());
    }

    /** What an objective asks for, without the amount (the "x / y" beside it carries that). */
    private static Component objectiveText(Packets.ChallengeView.ObjectiveView o) {
        String v = o.value() == null ? "" : o.value().trim();
        ResourceLocation id = v.isEmpty() ? null : ResourceLocation.tryParse(v.startsWith("#") ? v.substring(1) : v);
        return switch (o.type()) {
            case "COOK_RECIPE" -> id == null ? tr("o.cook_any") : tr("o.cook", JournalText.nameOf(id));
            case "COOK_CATEGORY" -> tr("o.cook_category", id == null ? Component.literal(v) : categoryName(id));
            case "COOK_FROM_MOD" -> tr("o.cook_mod", JournalText.modName(v));
            case "USE_INGREDIENT" -> id == null ? tr("o.use_any") : tr("o.use", JournalText.nameOf(id));
            case "USE_INGREDIENT_TAG" -> tr("o.use", id == null ? Component.literal(v) : tagName(id));
            case "USE_METHOD" -> id == null ? tr("o.cook_any") : tr("o.method", JournalText.method(id));
            case "EAT_RECIPE" -> id == null ? tr("o.eat_any") : tr("o.eat", JournalText.nameOf(id));
            case "COMPLETE_CHALLENGE" -> tr("o.complete", id == null ? Component.literal(v) : challengeName(id));
            default -> tr("o." + o.type().toLowerCase(java.util.Locale.ROOT));
        };
    }

    /** The daily card's goal: "用 烹饪锅 制作 6 份" — counts carry their unit. */
    private static Component goalWithAmount(Packets.ChallengeView.ObjectiveView o) {
        String unit = o.unique() ? "unit_kind" : o.type().startsWith("EAT") ? "unit_times"
                : o.type().equals("REACH_LEVEL") ? "unit_level" : "unit_portion";
        return objectiveText(o).copy().append(" ").append(Component.literal(String.valueOf(o.goal()))
                .withStyle(s -> s.withColor(JournalTheme.TEXT_ACCENT))).append(tr(unit));
    }

    private static Component categoryName(ResourceLocation id) {
        for (var view : ClientJournalState.collections()) {
            if (view.id().equals(id)) return view.literalTitle() ? Component.literal(view.titleKey()) : Component.translatable(view.titleKey());
            for (var sub : view.subcategories()) if (sub.id().equals(id))
                return sub.literalTitle() ? Component.literal(sub.titleKey()) : Component.translatable(sub.titleKey());
        }
        return Component.literal(id.getPath());
    }

    private static Component tagName(ResourceLocation id) {
        String key = "tag.item." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        if (net.minecraft.locale.Language.getInstance().has(key)) return Component.translatable(key);
        return Component.literal("#" + id.getPath());
    }

    private static Component challengeName(ResourceLocation id) {
        for (var c : ClientJournalState.challenges()) if (c.id().equals(id)) return title(c);
        return Component.literal(id.getPath());
    }

    private static ItemStack objectiveIcon(Packets.ChallengeView.ObjectiveView o) {
        ResourceLocation item = objectiveItem(o);
        if (item != null) return JournalText.stackOf(item);
        String v = o.value() == null ? "" : o.value().trim();
        if (o.type().equals("USE_METHOD") && !v.isEmpty()) {
            ResourceLocation method = ResourceLocation.tryParse(v);
            ItemStack station = method == null ? ItemStack.EMPTY : JournalText.station(method);
            if (!station.isEmpty()) return station;
        }
        return switch (o.type()) {
            case "EAT_RECIPE", "EAT_FOOD" -> new ItemStack(Items.BREAD);
            case "DISCOVER_RECIPE" -> new ItemStack(Items.WRITABLE_BOOK);
            case "SERVE_PORTIONS" -> new ItemStack(Items.CAKE);
            case "COOK_PERFECT", "MASTER_RECIPE" -> new ItemStack(Items.NETHER_STAR);
            default -> new ItemStack(Items.CAULDRON);
        };
    }

    /** The objective's item when it names one (dish or ingredient); null otherwise. */
    private static ResourceLocation objectiveItem(Packets.ChallengeView.ObjectiveView o) {
        if (o == null || o.value() == null || o.value().isBlank() || o.value().startsWith("#")) return null;
        if (!List.of("COOK_RECIPE", "USE_INGREDIENT", "EAT_RECIPE", "COOK_TODAY").contains(o.type())) return null;
        return ResourceLocation.tryParse(o.value().trim());
    }

    // ------------------------------------------------------------------ drawing helpers

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

    private static void thinBar(GuiGraphics g, int bx, int by, int bw, float fraction, boolean good) {
        thinBar(g, bx, by, bw, fraction, good, good ? 0xFF6F9147 : 0);
    }

    private static void thinBar(GuiGraphics g, int bx, int by, int bw, float fraction, boolean good, int muteColor) {
        if (bw <= 0) return;
        g.fill(bx, by, bx + bw, by + 3, JournalTheme.BAR_TRACK);
        int fill = Math.round(Math.max(0F, Math.min(1F, fraction)) * bw);
        for (int i = 0; i < fill; i++) {
            float t = fill <= 1 ? 0 : (float) i / (fill - 1);
            int color = muteColor != 0 ? muteColor : good ? JournalTheme.TEXT_GOOD : blend(JournalTheme.BAR_FILL_START, JournalTheme.BAR_FILL_END, t);
            g.fill(bx + i, by, bx + i + 1, by + 3, color);
        }
    }

    private static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gr = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }

    /** Darken a rectangular area by drawing a semi-transparent black overlay. */
    private static void wash(GuiGraphics g, int x, int y, int w, float darkness) {
        int alpha = (int) (darkness * 255);
        g.fill(x, y, x + w, y + w, (alpha << 24));
    }

    private boolean inside(int bx, int by, int bw, int bh) {
        return mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh && mouseY >= y && mouseY < y + height;
    }

    private float step(String slot, boolean hovered) {
        float t = grow.getOrDefault(slot, 0F);
        t = hovered ? Math.min(1F, t + frameStep) : Math.max(0F, t - frameStep);
        if (t <= 0F) grow.remove(slot); else grow.put(slot, t);
        return t;
    }

    private static void washIcon(GuiGraphics g, int ix, int iy, int size, float alpha) {
        g.pose().pushPose(); g.pose().translate(0, 0, 250);
        g.fill(ix, iy, ix + size, iy + size, ((int) ((1F - alpha) * 200) << 24) | 0x1A140F);
        g.pose().popPose();
    }

    /**
     * A bare 12px icon (no box, no background) that grows a quarter while hovered. With an item it
     * shows the item tooltip (plus {@code extra}) and opens the recipe viewer on click; without one
     * it shows {@code extra} as a plain tooltip.
     */
    private void growIcon(GuiGraphics g, String slot, ItemStack stack, int ix, int iy, float alpha,
                          ResourceLocation item, List<Component> extra) {
        boolean hovered = (item != null || !extra.isEmpty()) && inside(ix, iy, ICON, ICON);
        float t = step(slot, hovered);
        float eased = 1F - (1F - t) * (1F - t) * (1F - t);
        float scale = (1F + HOVER_GROW * eased) * ICON / 16F;
        g.pose().pushPose();
        g.pose().translate(ix + ICON / 2F, iy + ICON / 2F, t > 0F ? 50 : 0);
        g.pose().scale(scale, scale, 1F);
        g.renderFakeItem(stack, -8, -8);
        g.pose().popPose();
        if (alpha < 1F) {
            // The dimming overlay has to grow with the sprite, otherwise a hovered icon shows a
            // bright rim where it outgrew the fixed 12px square.
            int grown = Math.round(ICON * (1F + HOVER_GROW * eased));
            int over = (grown - ICON) / 2;
            washIcon(g, ix - over, iy - over, grown, alpha);
        }
        if (item != null || !extra.isEmpty()) hovers.add(0, new Hover(ix, iy, ICON, ICON, item, extra));
    }

    private void scaled(GuiGraphics g, Component text, int tx, int ty, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font(), text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Like {@link #scaled} but with a sub-pixel x, for centres that land between two pixels. */
    private void scaledAt(GuiGraphics g, Component text, float tx, int ty, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font(), text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** One line cut to {@code maxW} with "…"; the full text shows on hover when cut. */
    private void clipped(GuiGraphics g, Component text, int tx, int ty, int maxW, int color) {
        if (maxW <= 0) return;
        if (font().width(text) <= maxW) { g.drawString(font(), text, tx, ty, color, false); return; }
        var cut = font().substrByWidth(text, maxW - font().width("…"));
        g.drawString(font(), net.minecraft.locale.Language.getInstance().getVisualOrder(
                net.minecraft.network.chat.FormattedText.composite(cut, Component.literal("…"))), tx, ty, color, false);
        hovers.add(new Hover(tx, ty - 1, maxW, 10, null, List.of(text)));
    }

    private void clippedScaled(GuiGraphics g, Component text, int tx, int ty, int maxW, int color) {
        int raw = (int) (maxW / SMALL);
        if (raw <= 0) return;
        var shown = font().width(text) <= raw ? Language().getVisualOrder(text)
                : Language().getVisualOrder(net.minecraft.network.chat.FormattedText.composite(
                font().substrByWidth(text, raw - font().width("…")), Component.literal("…")));
        g.pose().pushPose(); g.pose().translate(tx, ty, 0); g.pose().scale(SMALL, SMALL, 1F);
        g.drawString(font(), shown, 0, 0, color, false);
        g.pose().popPose();
        if (font().width(text) > raw) hovers.add(new Hover(tx, ty - 1, maxW, 8, null, List.of(text)));
    }

    private static net.minecraft.locale.Language Language() { return net.minecraft.locale.Language.getInstance(); }

    // ------------------------------------------------------------------ input

    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < x || mx >= x + width || my < y || my >= y + height) return false;
        int previous = scroll;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (delta * SCROLL_STEP)));
        com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.scroll(previous, scroll);
        return true;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (mx < x || mx >= x + width || my < y || my >= y + height) return false;
        if (button == 0 && maxScroll() > 0 && mx >= x + width - 4) { dragging = true; drag(my); return true; }
        if (button == 0) for (Click click : clicks) {
            if (click.contains(mx, my)) {
                com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.click();
                click.action().run();
                return true;
            }
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
