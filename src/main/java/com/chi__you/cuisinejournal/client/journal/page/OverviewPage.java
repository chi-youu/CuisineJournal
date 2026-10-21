package com.chi__you.cuisinejournal.client.journal.page;

import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.client.journal.JournalPage;
import com.chi__you.cuisinejournal.client.journal.JournalText;
import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import com.chi__you.cuisinejournal.client.journal.ActivityPresentation;
import com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat;
import com.chi__you.cuisinejournal.culinary.ActivityEntry;
import com.chi__you.cuisinejournal.culinary.MealStamp;
import com.chi__you.cuisinejournal.mastery.MasteryRank;
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
 * The landing page: where am I, and what did I do lately.
 *
 * <p>Left column — overall progress: cooking level, dish / ingredient discovery and star
 * collection, each one line with its bar. Right column — recent: most prepared / eaten
 * (click to open the dish), open challenges (click for the challenges page), tracked recipes
 * (click to open the dish, "取消" on hover stops tracking) and recent activity with repeats
 * folded ("made X ×6"). Full totals live on the statistics page.</p>
 */
public final class OverviewPage extends BasePage {

    private static final int COLUMN_GAP = 12;
    private static final int ICON = 12;
    private static final float SMALL = 0.75F;

    private record Hit(int x, int y, int w, int h, ResourceLocation dish, boolean challenges,
                       MealStamp stamp) {
        Hit(int x, int y, int w, int h, ResourceLocation dish, boolean challenges) {
            this(x, y, w, h, dish, challenges, null);
        }

        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Hit> hits = new ArrayList<>();
    /** Plain buttons (stop tracking, expand); checked before {@link #hits} so they win over the row. */
    private final List<Action> actions = new ArrayList<>();
    private record Action(int x, int y, int w, int h, Runnable run) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private static final int TRACK_SHOWN = 3;
    private boolean trackingOpen;
    /** Inventory counts for tracked ingredients, refreshed at most once a second. */
    private Map<ResourceLocation, Integer> trackInventory = Map.of();
    private long trackInventoryAt;
    private long trackInventoryRevision = Long.MIN_VALUE;
    private final Map<String, Float> grow = new HashMap<>();
    private long lastFrame;
    private float frameStep;
    private int mouseX, mouseY;

    @Override
    public String id() {
        return "overview";
    }

    @Override
    public Component title() {
        return Component.translatable("cuisinejournal.page.overview");
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("cuisinejournal.overview." + key, args);
    }

    @Override
    public void render(GuiGraphics graphics, int mx, int my, float partialTick) {
        hits.clear();
        actions.clear();
        mouseX = mx; mouseY = my;
        long now = System.nanoTime();
        frameStep = (lastFrame == 0 ? 0F : Math.min(0.1F, (now - lastFrame) / 1_000_000_000F)) / 0.12F;
        lastFrame = now;
        // Two columns when there is room, one when the window is narrow.
        boolean split = width >= 250;
        int columnWidth = split ? (width - COLUMN_GAP) / 2 : width;
        graphics.enableScissor(x,y,x+width,y+height);
        renderProgress(graphics, x, columnWidth);
        if (split) renderRecent(graphics, x + columnWidth + COLUMN_GAP, width - columnWidth - COLUMN_GAP);
        graphics.disableScissor();
        if(mx<x||mx>=x+width||my<y||my>=y+height)return;
        for (Hit hit : hits) {
            if (hit.dish() == null || !hit.contains(mx, my)) continue;
            ItemStack stack = JournalText.stackOf(hit.dish());
            // A preparation hit carries the stars and cook it had at the time, so the
            // tooltip reads like the stack that came out of the kitchen that day.
            if (hit.stamp() != null) hit.stamp().write(stack);
            JournalText.renderItemTooltip(graphics, font(), stack,
                    List.of(Component.translatable("cuisinejournal.overview.open_dish")
                            .withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT))), mx, my);
            break;
        }
    }

    // ------------------------------------------------------------ left column

    private void renderProgress(GuiGraphics g, int columnX, int columnWidth) {
        int right = columnX + columnWidth;
        // Page heading, same treatment as the other pages' titles: bold, slightly larger.
        MutableComponent heading = tr("profile").withStyle(st -> st.withBold(true));
        scaled(g, heading, columnX, y + 1, 10F / 9F, JournalTheme.TEXT_ACCENT);
        JournalTheme.divider(g, columnX, y + 11, right - columnX);
        int cursor = y + 17;

        g.drawString(font(), tr("level_line", Component.literal(String.valueOf(ClientJournalState.cookingLevel()))
                .withStyle(st -> st.withColor(JournalTheme.TEXT_ACCENT))), columnX, cursor, JournalTheme.TEXT, false);
        long into = ClientJournalState.xpIntoLevel(), needed = ClientJournalState.xpForCurrentLevel();
        if (!JournalText.cozy()) {
            Component xp = Component.literal(needed < 0 ? JournalText.number(into) + " XP"
                    : JournalText.number(into) + " / " + JournalText.number(needed));
            JournalTheme.value(g, font(), xp, right, cursor, JournalTheme.TEXT_DIM);
        }
        cursor += 11;
        JournalTheme.bar(g, columnX, cursor, right - columnX, 6, needed <= 0 ? 1F : (float) into / needed, needed < 0);
        cursor += 10;

        int total = ClientJournalState.dishCount(), discovered = ClientJournalState.discoveredCount();
        int ingredients = ClientJournalState.ingredientsDiscovered(), ingredientTotal = ClientJournalState.totalIngredients();
        int[] stars = ClientJournalState.starSummary();
        cursor = row(g, columnX, right, cursor, tr("discovered_dishes"), fraction(discovered, total));
        cursor = row(g, columnX, right, cursor, tr("discovered_ingredients"), fraction(ingredients, ingredientTotal));
        cursor = row(g, columnX, right, cursor, tr("stars_earned"), fraction(stars[0], stars[1]));
        cursor = row(g, columnX, right, cursor, tr("prepared"), plain(ClientJournalState.mealsPrepared()));
        cursor = row(g, columnX, right, cursor, tr("eaten"), plain(ClientJournalState.mealsEaten()));

        cursor = header(g, columnX, right, cursor + 6, tr("completion"), null);
        cursor = labelledBar(g, columnX, right, cursor, tr("dish_discovery"), total <= 0 ? 0F : (float) discovered / total);
        cursor = labelledBar(g, columnX, right, cursor, tr("ingredient_discovery"),
                ingredientTotal <= 0 ? 0F : (float) ingredients / ingredientTotal);
        cursor = labelledBar(g, columnX, right, cursor, tr("stars"), stars[1] <= 0 ? 0F : (float) stars[0] / stars[1]);
        // "满星小类 0 / 62 个" — or, with per-dish stars, "精通料理 0 / 1,144 道".
        int groups = stars[1] / Math.max(1, MasteryRank.highestIndex());
        boolean perDish = ClientJournalState.perDishMastery();
        Component full = tr(perDish ? "full_dishes" : "full_groups",
                Component.literal(JournalText.number(stars[2])).withStyle(st -> st.withColor(JournalTheme.TEXT_ACCENT)),
                Component.literal(JournalText.number(groups)).withStyle(st -> st.withColor(JournalTheme.TEXT_DIM)));
        scaled(g, full, columnX, cursor, SMALL, JournalTheme.TEXT_FAINT);
    }

    private static Component fraction(long value, long total) {
        return Component.literal(JournalText.number(value)).withStyle(st -> st.withColor(JournalTheme.TEXT))
                .append(Component.literal(" / " + JournalText.number(total)).withStyle(st -> st.withColor(JournalTheme.TEXT_FAINT)));
    }

    private static Component plain(long value) {
        return Component.literal(JournalText.number(value));
    }

    /** Label on the left, value on the right, one 11px line (as the original overview). */
    private int row(GuiGraphics g, int left, int right, int cursor, Component label, Component value) {
        if (cursor + 10 > bottom()) return cursor;
        g.drawString(font(), label, left, cursor, JournalTheme.TEXT_DIM, false);
        if (!JournalText.cozy()) JournalTheme.value(g, font(), value, right, cursor, JournalTheme.TEXT);
        return cursor + 11;
    }

    /** Label + percentage, then the original 5px bar. */
    private int labelledBar(GuiGraphics g, int left, int right, int cursor, Component label, float fraction) {
        g.drawString(font(), label, left, cursor, JournalTheme.TEXT_DIM, false);
        if (!JournalText.cozy())
            JournalTheme.value(g, font(), Component.literal(JournalText.percent(fraction)), right, cursor, JournalTheme.TEXT);
        cursor += 10;
        JournalTheme.bar(g, left, cursor, right - left, 5, fraction, fraction >= 1F);
        return cursor + 9;
    }

    // ----------------------------------------------------------- right column

    private void renderRecent(GuiGraphics g, int columnX, int columnWidth) {
        int right = columnX + columnWidth;
        int cursor = renderHighlights(g, columnX, right, y);
        cursor = renderChallenges(g, columnX, right, cursor);
        cursor = renderTracking(g, columnX, right, cursor);
        renderActivity(g, columnX, right, cursor);
    }

    private int renderHighlights(GuiGraphics g, int columnX, int right, int cursor) {
        List<Packets.RecordView> records = ClientJournalState.records();
        Packets.RecordView mostPrepared = records.stream().filter(r -> r.prepared() > 0L)
                .max(Comparator.comparingLong(Packets.RecordView::prepared)).orElse(null);
        Packets.RecordView mostEaten = records.stream().filter(r -> r.eaten() > 0L)
                .max(Comparator.comparingLong(Packets.RecordView::eaten)).orElse(null);
        if (mostPrepared == null && mostEaten == null) return cursor;
        cursor = header(g, columnX, right, cursor, tr("highlights"), null);
        if (mostPrepared != null) cursor = dishRow(g, columnX, right, cursor, "most_prepared",
                mostPrepared.target(), mostPrepared.prepared());
        if (mostEaten != null) cursor = dishRow(g, columnX, right, cursor, "most_eaten",
                mostEaten.target(), mostEaten.eaten());
        return cursor + 4;
    }

    /** One line: small label, icon (grows on hover), dish name, count. Clicking opens the dish. */
    private int dishRow(GuiGraphics g, int left, int right, int cursor, String labelKey, ResourceLocation dish, long count) {
        if (cursor + 14 > bottom()) return cursor;
        int labelW = (int) Math.ceil(font().width(tr(labelKey)) * SMALL);
        scaled(g, tr(labelKey), left, cursor + 3, SMALL, JournalTheme.TEXT_FAINT);
        int iconX = left + Math.max(labelW, 36) + 4;
        boolean hover = mouseX >= left && mouseX < right && mouseY >= cursor && mouseY < cursor + 13;
        growIcon(g, labelKey, JournalText.stackOf(dish), iconX, cursor, hover, ICON);
        g.drawString(font(), font().plainSubstrByWidth(JournalText.nameOf(dish).getString(), right - iconX - ICON - 40),
                iconX + ICON + 4, cursor + 2, hover ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT, false);
        if (!JournalText.cozy()) {
            Component times = Component.literal(JournalText.number(count)).withStyle(s -> s.withColor(JournalTheme.TEXT_ACCENT))
                    .append(Component.translatable("cuisinejournal.statistics.unit_times").withStyle(s -> s.withColor(JournalTheme.TEXT_DIM)));
            JournalTheme.value(g, font(), times, right, cursor + 2, JournalTheme.TEXT);
        }
        hits.add(new Hit(left, cursor, right - left, 13, dish, false));
        return cursor + 14;
    }

    private int renderChallenges(GuiGraphics g, int left, int right, int cursor) {
        List<Packets.ChallengeView> open = ClientJournalState.challenges().stream()
                .filter(challenge -> !challenge.completed() && !challenge.locked())
                .sorted(Comparator.comparingDouble((Packets.ChallengeView c) -> c.goal() <= 0 ? 0
                        : -(double) c.progress() / c.goal()))
                .limit(3).toList();
        if (open.isEmpty() || cursor + 28 > bottom()) return cursor;
        cursor = header(g, left, right, cursor, Component.translatable("cuisinejournal.page.challenges"), tr("view_all"));
        // Only "查看全部" opens the challenges page.
        Component more = tr("view_all");
        int moreW = (int) Math.ceil(font().width(more) * SMALL);
        int moreY = cursor - 16;
        boolean moreHover = mouseX >= right - moreW - 2 && mouseX < right + 2 && mouseY >= moreY && mouseY < moreY + 10;
        if (moreHover) scaled(g, more, right - moreW, moreY + 2, SMALL, JournalTheme.TEXT);
        hits.add(new Hit(right - moreW - 2, moreY, moreW + 4, 10, null, true));
        // One fixed column for the "12 / 25" values, sized by the widest, so every bar lines up.
        int valueColumn = 0;
        if (!JournalText.cozy()) for (Packets.ChallengeView challenge : open)
            if (challenge.goal() > 0) valueColumn = Math.max(valueColumn,
                    font().width(challenge.progress() + " / " + challenge.goal()));
        int barW = 34;
        int barX = right - valueColumn - (valueColumn > 0 ? 6 : 0) - barW;
        for (Packets.ChallengeView challenge : open) {
            if (cursor + 12 > bottom()) return cursor;
            if (!JournalText.cozy() && challenge.goal() > 0) {
                Component value = Component.literal(String.valueOf(challenge.progress())).withStyle(st -> st.withColor(JournalTheme.TEXT))
                        .append(Component.literal(" / " + challenge.goal()).withStyle(st -> st.withColor(JournalTheme.TEXT_FAINT)));
                JournalTheme.value(g, font(), value, right, cursor, JournalTheme.TEXT);
            }
            Component name = Component.translatable(challenge.titleKey());
            int nameW = barX - left - 6;
            g.drawString(font(), font().plainSubstrByWidth(name.getString(), nameW), left, cursor, JournalTheme.TEXT_DIM, false);
            if (challenge.goal() > 0)
                JournalTheme.bar(g, barX, cursor + 2, barW, 4,
                        (float) challenge.progress() / challenge.goal(), false);
            cursor += 11;
        }
        return cursor;
    }

    /**
     * "追踪中": one line per tracked recipe — icon, name with "×target", and how many ingredient
     * slots the inventory already covers. Hovering a line swaps that count for a "取消" link; the
     * rest of the line opens the dish. Hidden entirely when nothing is tracked.
     */
    private int renderTracking(GuiGraphics g, int left, int right, int cursor) {
        var features = ClientJournalState.journalFeatures();
        if (features == null || features.tracks().isEmpty() || cursor + 30 > bottom()) return cursor;
        var tracks = features.tracks();
        cursor = header(g, left, right, cursor, tr("tracking"), null);
        refreshTrackInventory(tracks);
        int shown = trackingOpen ? tracks.size() : Math.min(TRACK_SHOWN, tracks.size());
        Component cancel = Component.translatable("cuisinejournal.overview.tracking_cancel");
        int cancelW = font().width(cancel);
        for (int i = 0; i < shown; i++) {
            if (cursor + 14 > bottom()) return cursor;
            var t = tracks.get(i);
            boolean hover = mouseX >= left && mouseX < right && mouseY >= cursor && mouseY < cursor + 13;
            if (hover) g.fill(left - 2, cursor - 1, right + 2, cursor + 13, 0x14FFE9C7);
            growIcon(g, "track/" + i, JournalText.stackOf(t.dish()), left, cursor, hover, ICON);
            // Right side: "取消" while hovered, otherwise "ready / slots 种".
            int rightEdge;
            if (hover) {
                int cx = right - cancelW;
                boolean onCancel = mouseX >= cx - 2 && mouseX < right + 2 && mouseY >= cursor && mouseY < cursor + 13;
                g.drawString(font(), cancel, cx, cursor + 2, onCancel ? JournalTheme.TEXT : JournalTheme.TEXT_DIM, false);
                if (onCancel) g.fill(cx, cursor + 11, right, cursor + 12, JournalTheme.TEXT_DIM);
                var dish = t.dish();
                actions.add(new Action(cx - 2, cursor, cancelW + 4, 13, () -> com.chi__you.cuisinejournal.network.Network.toServer(
                        new com.chi__you.cuisinejournal.network.JournalFeaturePackets.Track(dish, null, 0))));
                rightEdge = cx - 6;
            } else {
                int ready = readySlots(t), slots = t.slots().size();
                Component count = Component.literal(String.valueOf(ready))
                        .withStyle(st -> st.withColor(ready >= slots ? JournalTheme.TEXT_GOOD : JournalTheme.TEXT))
                        .append(Component.literal(" / " + slots).withStyle(st -> st.withColor(JournalTheme.TEXT_FAINT)))
                        .append(Component.translatable("cuisinejournal.overview.tracking_kinds").withStyle(st -> st.withColor(JournalTheme.TEXT_FAINT)));
                int countW = font().width(count);
                JournalTheme.value(g, font(), count, right, cursor + 2, JournalTheme.TEXT);
                rightEdge = right - countW - 6;
            }
            // Name, then "×target" in small gold right after it.
            int nameX = left + ICON + 4;
            Component times = Component.literal("×" + t.target());
            int timesW = (int) Math.ceil(font().width(times) * SMALL);
            String name = JournalText.nameOf(t.dish()).getString();
            int nameMax = Math.max(0, rightEdge - nameX - timesW - 4);
            if (font().width(name) > nameMax) name = font().plainSubstrByWidth(name, Math.max(0, nameMax - font().width("…"))) + "…";
            g.drawString(font(), name, nameX, cursor + 2, hover ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT, false);
            scaled(g, times, nameX + font().width(name) + 4, cursor + 4, SMALL, JournalTheme.TEXT_ACCENT);
            hits.add(new Hit(left, cursor, rightEdge - left, 13, t.dish(), false));
            cursor += 14;
        }
        if (tracks.size() > TRACK_SHOWN && cursor + 10 <= bottom()) {
            Component more = trackingOpen ? tr("tracking_less") : tr("tracking_more", tracks.size() - TRACK_SHOWN);
            int mw = (int) Math.ceil(font().width(more) * SMALL);
            boolean hover = mouseX >= left && mouseX < left + mw + 4 && mouseY >= cursor && mouseY < cursor + 10;
            scaled(g, more, left, cursor + 1, SMALL, hover ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT);
            actions.add(new Action(left, cursor, mw + 4, 10, () -> trackingOpen = !trackingOpen));
            cursor += 11;
        }
        return cursor;
    }

    /** How many ingredient slots the inventory fully covers, using the HUD's allocation. */
    private int readySlots(com.chi__you.cuisinejournal.network.JournalFeaturePackets.Tracked t) {
        var needs = t.slots().stream().map(sl -> new com.chi__you.cuisinejournal.progression.RecipeTracking.Need(sl.items(), sl.needed())).toList();
        int[] supplied = com.chi__you.cuisinejournal.progression.RecipeTracking.allocate(needs, trackInventory);
        int ready = 0;
        for (int i = 0; i < supplied.length; i++) if (supplied[i] >= t.slots().get(i).needed()) ready++;
        return ready;
    }

    private void refreshTrackInventory(List<com.chi__you.cuisinejournal.network.JournalFeaturePackets.Tracked> tracks) {
        long now = System.currentTimeMillis();
        if (now - trackInventoryAt < 1000 && trackInventoryRevision == ClientJournalState.revision()) return;
        trackInventoryAt = now;
        trackInventoryRevision = ClientJournalState.revision();
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) { trackInventory = Map.of(); return; }
        java.util.Set<ResourceLocation> wanted = new java.util.HashSet<>();
        for (var t : tracks) for (var sl : t.slots()) wanted.addAll(sl.items());
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (wanted.contains(id)) counts.merge(id, stack.getCount(), Integer::sum);
        }
        trackInventory = counts;
    }

    private void renderActivity(GuiGraphics g, int left, int right, int cursor) {
        int lines = com.chi__you.cuisinejournal.config.ConfigManager.client().journal.recentActivityLines;
        if (lines == 0 || cursor + 24 > bottom()) return;
        cursor = header(g, left, right, cursor, tr("recent"), null);
        var folded = ActivityPresentation.fold(ClientJournalState.activity());
        if (folded.isEmpty()) {
            for (var line : font().split(Component.translatable("cuisinejournal.empty.activity"), right - left)) {
                g.drawString(font(), line, left, cursor, JournalTheme.TEXT_FAINT, false);
                cursor += 10;
            }
            return;
        }
        int shown = 0;
        for (ActivityPresentation.Folded item : folded) {
            if (shown >= lines || cursor + 12 > bottom()) return;
            Packets.ActivityView entry = item.entry();
            ActivityEntry.Type type = ActivityEntry.Type.byId(entry.type());
            ItemStack icon = activityIcon(type, entry.subject());
            MealStamp stamp = ActivityPresentation.stampFor(entry);
            if (stamp != null && !icon.isEmpty()) stamp.write(icon);
            int color = entry.perfect() ? ActivityPresentation.PERFECT_COLOR
                    : important(type) ? JournalTheme.TEXT : JournalTheme.TEXT_DIM;
            int iconX = left;
            boolean linked = entry.subject() != null && isDish(type);
            boolean iconHover = linked && mouseX >= iconX && mouseX < iconX + 10 && mouseY >= cursor - 1 && mouseY < cursor + 9;
            growIcon(g, "activity/" + shown, icon, iconX - 1, cursor - 2, iconHover, 10);
            if (linked) hits.add(new Hit(iconX, cursor - 1, 10, 10, entry.subject(), false, stamp));
            // Day on the right (faint), "×6" in gold before it.
            int rightEdge = right;
            if (entry.day() >= 0) {
                Component day = Component.translatable("cuisinejournal.statistics.on_day", entry.day());
                int dayW = (int) Math.ceil(font().width(day) * SMALL);
                scaled(g, day, right - dayW, cursor + 1, SMALL, JournalTheme.TEXT_FAINT);
                rightEdge -= dayW + 5;
            }
            if (item.count() > 1) {
                Component times = Component.literal("×" + item.count());
                JournalTheme.value(g, font(), times, rightEdge, cursor,
                        entry.perfect() ? ActivityPresentation.PERFECT_COLOR : JournalTheme.TEXT_ACCENT);
                rightEdge -= font().width(times) + 4;
            }
            Component line = Component.translatable(entry.perfect() && ActivityPresentation.preparation(entry)
                    ? "cuisinejournal.activity.meal_prepared_perfect" : "cuisinejournal.activity." + entry.type(), argumentsFor(entry));
            int textX = iconX + 14;
            String text = line.getString();
            if (font().width(text) > rightEdge - textX)
                text = font().plainSubstrByWidth(text, Math.max(0, rightEdge - textX - font().width("…"))) + "…";
            g.drawString(font(), text, textX, cursor, color, false);
            cursor += 11;
            shown++;
        }
    }

    private static boolean important(ActivityEntry.Type type) {
        return type != ActivityEntry.Type.MEAL_PREPARED && type != ActivityEntry.Type.INGREDIENT_DISCOVERED;
    }

    private static boolean isDish(ActivityEntry.Type type) {
        return type == ActivityEntry.Type.MEAL_PREPARED || type == ActivityEntry.Type.RECIPE_DISCOVERED
                || type == ActivityEntry.Type.MASTERY_RANK || type == ActivityEntry.Type.MEAL_MASTERED;
    }

    /** The subject's own item where there is one; otherwise a vanilla stand-in for the event. */
    private static ItemStack activityIcon(ActivityEntry.Type type, ResourceLocation subject) {
        if (subject != null && type != ActivityEntry.Type.METHOD_DISCOVERED
                && type != ActivityEntry.Type.CHALLENGE_COMPLETED && type != ActivityEntry.Type.MILESTONE_REACHED
                && type != ActivityEntry.Type.COLLECTION_COMPLETED) {
            ItemStack stack = JournalText.stackOf(subject);
            if (!stack.isEmpty() && !stack.is(Items.AIR)) return stack;
        }
        return new ItemStack(switch (type) {
            case LEVEL_UP -> Items.EXPERIENCE_BOTTLE;
            case CHALLENGE_COMPLETED -> Items.GOLD_INGOT;
            case MILESTONE_REACHED -> Items.GOLDEN_APPLE;
            case COLLECTION_COMPLETED -> Items.CHEST;
            case METHOD_DISCOVERED -> Items.CRAFTING_TABLE;
            case MASTERY_RANK, MEAL_MASTERED -> Items.NETHER_STAR;
            default -> Items.BOWL;
        });
    }

    // ----------------------------------------------------------------- pieces

    /** Section title in gold with a full-width divider underneath (original overview style). */
    private int header(GuiGraphics g, int left, int right, int cursor, Component title, Component note) {
        if (cursor > y) cursor += 4;
        g.drawString(font(), title, left, cursor, JournalTheme.TEXT_ACCENT, false);
        if (note != null) {
            int noteW = (int) Math.ceil(font().width(note) * SMALL);
            scaled(g, note, right - noteW, cursor + 2, SMALL, JournalTheme.TEXT_FAINT);
        }
        JournalTheme.divider(g, left, cursor + 10, right - left);
        return cursor + 16;
    }

    private void growIcon(GuiGraphics g, String slot, ItemStack stack, int ix, int iy, boolean hovered, int size) {
        float t = grow.getOrDefault(slot, 0F);
        t = hovered ? Math.min(1F, t + frameStep) : Math.max(0F, t - frameStep);
        if (t <= 0F) grow.remove(slot); else grow.put(slot, t);
        float eased = 1F - (1F - t) * (1F - t) * (1F - t);
        float scale = (1F + 0.25F * eased) * size / 16F;
        g.pose().pushPose();
        g.pose().translate(ix + size / 2F, iy + size / 2F, t > 0F ? 50 : 0);
        g.pose().scale(scale, scale, 1F);
        g.renderFakeItem(stack, -8, -8);
        g.pose().popPose();
    }

    private void scaled(GuiGraphics g, Component text, int tx, int ty, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font(), text, 0, 0, color, false);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || screen == null) return false;
        if(mx<x||mx>=x+width||my<y||my>=y+height)return false;
        for (Action action : actions) {
            if (!action.contains(mx, my)) continue;
            CuisineUiSoundCompat.click();
            action.run().run();
            return true;
        }
        for (Hit hit : hits) {
            if (!hit.contains(mx, my)) continue;
            if (hit.challenges()) {
                JournalPage challenges = screen.page("challenges");
                if (challenges != null) screen.select(challenges);
                return true;
            }
            if (hit.dish() != null && screen.page("collections") instanceof CollectionsPage collections) {
                CuisineUiSoundCompat.click();
                screen.jump(this, collections);
                if (!collections.openFood(hit.dish())) screen.select(this);
                return true;
            }
        }
        return false;
    }

    @Override
    public Component emptyMessage() {
        return Component.translatable("cuisinejournal.empty.activity");
    }

    /**
     * The arguments one activity line's format string expects. A rank change names the rank
     * as well, and a level-up has no subject — its number is in {@code detail}.
     */
    private static Object[] argumentsFor(Packets.ActivityView entry) {
        Component subject = entry.subject() == null ? Component.empty() : subjectName(entry);
        return switch (ActivityEntry.Type.byId(entry.type())) {
            case MASTERY_RANK -> new Object[]{subject, Component.translatable(
                    MasteryRank.byIndex((int) entry.detail()).translationKey())};
            case LEVEL_UP -> new Object[]{entry.detail()};
            default -> new Object[]{subject};
        };
    }

    private static Component subjectName(Packets.ActivityView entry) {
        ResourceLocation id = entry.subject();
        return switch (ActivityEntry.Type.byId(entry.type())) {
            case METHOD_DISCOVERED -> JournalText.method(id);
            case CHALLENGE_COMPLETED -> Component.translatable(
                    "cuisinejournal.challenge." + id.getNamespace() + "." + id.getPath() + ".title");
            case MILESTONE_REACHED -> Component.translatable(
                    "cuisinejournal.milestone." + id.getNamespace() + "." + id.getPath());
            default -> JournalText.nameOf(id);
        };
    }
}
