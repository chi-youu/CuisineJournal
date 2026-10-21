package com.chi__you.cuisinejournal.client.journal;

import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.client.FlavorClientState;
import com.chi__you.cuisinejournal.config.ClientConfig;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.food.tooltip.TooltipText;
import com.chi__you.cuisinejournal.mastery.MasteryRank;
import com.chi__you.cuisinejournal.network.Packets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.Rect2i;

import java.util.List;

/**
 * The stats panel docked beside a cooking screen.
 *
 * <p>Shows culinary progress next to whatever the player is cooking on, so
 * mastery is visible while cooking rather than only inside the journal.</p>
 *
 * <p>Drawn entirely from code and item icons. The caller supplies the GUI
 * box because {@code leftPos} and {@code imageWidth} are protected on
 * {@code AbstractContainerScreen} and each loader reaches them differently.</p>
 *
 * <p>Every label/value row is measured before it is drawn: values are
 * right-aligned, so a long translation would otherwise run straight into its
 * own label.</p>
 */
public final class StatsSidePanel {

    private static final int WIDTH = 130;
    private static final int GAP = 6;
    private static final int PADDING = 6;
    private static final int HEAD = 16;
    private static final float SMALL = 0.75F;

    private StatsSidePanel() {
    }

    /**
     * @param guiLeft/guiTop/guiWidth the container GUI's box, in screen pixels
     * @param screenWidth/screenHeight the window, in the same scaled pixels
     */
    public static void render(GuiGraphics graphics, int guiLeft, int guiTop, int guiWidth,
                              int screenWidth, int screenHeight, AbstractContainerMenu menu) {
        Rect2i area = extraArea(menu, guiLeft, guiTop, guiWidth, screenWidth, screenHeight);
        if (area == null) return;
        ResourceLocation focus = focusedDish(menu);
        Font font = Minecraft.getInstance().font;
        int x = area.getX(), y = area.getY(), height = area.getHeight();

        JournalTheme.panel(graphics, x, y, WIDTH, height);
        int left = x + PADDING;
        int right = x + WIDTH - PADDING;
        int cursorY = y + PADDING;

        cursorY = renderHeader(graphics, font, left, right, cursorY);
        if (focus != null) {
            JournalTheme.divider(graphics, left, cursorY + 2, right - left);
            cursorY = renderDish(graphics, font, left, right, cursorY + 6, focus);
        }
        JournalTheme.divider(graphics, left, cursorY + 2, right - left);
        renderToday(graphics, font, left, right, cursorY + 6);
    }

    /** The rectangle reported to JEI as an area it must leave unobstructed. */
    public static Rect2i extraArea(AbstractContainerMenu menu, int guiLeft, int guiTop,
                                   int guiWidth, int screenWidth, int screenHeight) {
        ClientConfig config = ConfigManager.client();
        if (config.journal.sidePanel == ClientConfig.SidePanel.OFF
                || !ClientJournalState.isCatalogueComplete()
                || isPersonalScreen(menu)) {
            return null;
        }
        if (config.journal.sidePanel == ClientConfig.SidePanel.CULINARY
                && !ClientJournalState.isMenuCulinary()
                && !isVanillaCookingMenu(menu)) {
            return null;
        }
        ResourceLocation focus = focusedDish(menu);
        int height = measure(focus);
        int x = guiLeft + guiWidth + GAP;
        if (x + WIDTH > screenWidth) {
            int onLeft = guiLeft - WIDTH - GAP;
            x = onLeft >= 0 ? onLeft : Math.max(0, screenWidth - WIDTH);
        }
        int y = Math.max(0, Math.min(guiTop, screenHeight - height));
        return new Rect2i(x, y, WIDTH, height);
    }

    /** Height is computed up front, with the same steps the renderer takes, so nothing clips. */
    private static int measure(ResourceLocation focus) {
        int height = PADDING + headerHeight();
        if (focus != null) height += 6 + dishHeight(focus);
        return height + 6 + 9 + PADDING;
    }

    private static int headerHeight() {
        return HEAD + 3 + 3 + (JournalText.cozy() ? 0 : 3 + 6);
    }

    private static int dishHeight(ResourceLocation dish) {
        int height = HEAD;
        if (!ClientJournalState.isDiscovered(dish)) return height + 3 + 9;
        height += 4 + 9 + 3 + 5;                                   // stars, bar
        if (!JournalText.cozy() && ClientJournalState.record(dish) != null) height += 5 + 8;
        List<ResourceLocation> flavors = FlavorClientState.dishFlavors(dish);
        if (flavors != null && !flavors.isEmpty()) height += 4 + 10;
        return height;
    }

    // ------------------------------------------------------------------ header

    /** Station icon and name (the panel's title), level on the right, then a thin XP bar. */
    private static int renderHeader(GuiGraphics graphics, Font font, int left, int right, int cursorY) {
        ResourceLocation method = ClientJournalState.menuMethod();
        ItemStack station = JournalText.station(method);
        Component level = Component.literal("Lv." + ClientJournalState.cookingLevel());
        int levelWidth = font.width(level);
        int textX = left;
        if (!station.isEmpty()) {
            graphics.renderFakeItem(station, left, cursorY);
            textX = left + 19;
        }
        Component title = method != null ? JournalText.method(method)
                : Component.translatable("cuisinejournal.journal.title");
        drawTruncated(graphics, font, title.copy().withStyle(s -> s.withBold(true)), textX, cursorY + 4,
                right - levelWidth - 6 - textX, JournalTheme.TEXT);
        graphics.drawString(font, level, right - levelWidth, cursorY + 4, JournalTheme.TEXT_ACCENT, false);
        cursorY += HEAD + 3;

        long into = ClientJournalState.xpIntoLevel();
        long needed = ClientJournalState.xpForCurrentLevel();
        thinBar(graphics, left, cursorY, right - left, needed <= 0 ? 1.0F : (float) into / needed);
        cursorY += 3;
        if (!JournalText.cozy()) {
            cursorY += 3;
            small(graphics, font, Component.translatable("cuisinejournal.panel.xp"), left, cursorY, JournalTheme.TEXT_FAINT);
            Component xp = Component.literal(needed < 0 ? JournalText.number(into) + " xp"
                    : JournalText.number(into) + " / " + JournalText.number(needed));
            small(graphics, font, xp, right - Math.round(font.width(xp) * SMALL), cursorY, JournalTheme.TEXT_FAINT);
            cursorY += 6;
        }
        return cursorY;
    }

    // ------------------------------------------------------------------ dish

    private static int renderDish(GuiGraphics graphics, Font font, int left, int right,
                                  int cursorY, ResourceLocation dish) {
        boolean known = ClientJournalState.isDiscovered(dish);
        graphics.renderFakeItem(JournalText.stackOf(dish), left, cursorY);
        int nameRight = right;
        if (!known) {
            // "新菜" tag: gold text in a faint gold box at the end of the name line.
            Component tag = Component.translatable("cuisinejournal.panel.new");
            int tagWidth = font.width(tag) + 6;
            int tagX = right - tagWidth;
            graphics.fill(tagX, cursorY + 2, right, cursorY + 14, 0x1FE8A93C);
            JournalTheme.border(graphics, tagX, cursorY + 2, tagWidth, 12, 0x80E8A93C);
            graphics.drawString(font, tag, tagX + 3, cursorY + 4, JournalTheme.TEXT_ACCENT, false);
            nameRight = tagX - 4;
        }
        drawTruncated(graphics, font, JournalText.nameOf(dish).copy().withStyle(s -> s.withBold(true)),
                left + 19, cursorY + 4, nameRight - left - 19, JournalTheme.TEXT);
        cursorY += HEAD;

        if (!known) {
            graphics.drawString(font, Component.translatable("cuisinejournal.panel.undiscovered"),
                    left, cursorY + 3, JournalTheme.TEXT_FAINT, false);
            return cursorY + 3 + 9;
        }

        // Stars on the left; "小类 · 段位" on the right (only the rank when stars are per dish).
        cursorY += 4;
        MasteryRank rank = ClientJournalState.rank(dish);
        boolean mastered = rank.isMastered();
        int filled = rank.ordinal();
        int starX = left;
        for (int i = 0; i < MasteryRank.highestIndex(); i++) {
            graphics.drawString(font, TooltipText.star(i < filled ? JournalTheme.TEXT_ACCENT : 0x4A3E33), starX, cursorY,
                    0xFFFFFFFF, false);
            starX += 9;
        }
        Component rankName = Component.translatable(rank.translationKey());
        int rankWidth = font.width(rankName);
        graphics.drawString(font, rankName, right - rankWidth, cursorY,
                mastered ? JournalTheme.TEXT_GOOD : JournalTheme.TEXT_DIM, false);
        Component sub = ClientJournalState.perDishMastery() ? null : subcategoryName(dish);
        if (sub != null) {
            Component dot = Component.literal(" · ");
            int dotX = right - rankWidth - font.width(dot);
            graphics.drawString(font, dot, dotX, cursorY, JournalTheme.TEXT_FAINT, false);
            int subLeft = starX + 4;
            int subWidth = Math.min(font.width(sub), dotX - subLeft);
            if (subWidth > font.width("...")) {
                drawTruncated(graphics, font, sub, dotX - subWidth, cursorY, subWidth, JournalTheme.TEXT_DIM);
            }
        }
        cursorY += 9 + 3;
        JournalTheme.bar(graphics, left, cursorY, right - left, 5, JournalText.masteryFraction(dish), mastered);
        cursorY += 5;

        Packets.RecordView record = ClientJournalState.record(dish);
        if (!JournalText.cozy() && record != null) {
            cursorY += 5;
            Component line = Component.translatable("cuisinejournal.panel.cooked")
                    .append(Component.literal(" " + JournalText.number(record.prepared()))
                            .withStyle(s -> s.withBold(true).withColor(JournalTheme.TEXT)))
                    .append("   ")
                    .append(Component.translatable("cuisinejournal.panel.eaten"))
                    .append(Component.literal(" " + JournalText.number(record.eaten()))
                            .withStyle(s -> s.withBold(true).withColor(JournalTheme.TEXT)));
            drawTruncated(graphics, font, line, left, cursorY, right - left, JournalTheme.TEXT_DIM);
            cursorY += 8;
        }

        List<ResourceLocation> flavors = FlavorClientState.dishFlavors(dish);
        if (flavors != null && !flavors.isEmpty()) {
            cursorY += 4;
            Component label = Component.translatable("cuisinejournal.flavor.title");
            small(graphics, font, label, left, cursorY + 2, JournalTheme.TEXT_FAINT);
            int fx = left + Math.round(font.width(label) * SMALL) + 5;
            for (ResourceLocation id : flavors) {
                FlavorClientState.Entry entry = FlavorClientState.get(id);
                Component name = FlavorUi.name(id);
                int needed = 12 + font.width(name);
                if (fx + needed > right) {
                    if (fx + font.width("…") <= right) graphics.drawString(font, "…", fx, cursorY + 1, JournalTheme.TEXT_FAINT, false);
                    break;
                }
                if (entry != null) FlavorUi.icon(graphics, entry.preset(), fx, cursorY, 10);
                else FlavorUi.leaf(graphics, fx, cursorY, 10);
                graphics.drawString(font, name, fx + 12, cursorY + 1, JournalTheme.DISCOVERY, false);
                fx += needed + 6;
            }
            cursorY += 10;
        }
        return cursorY;
    }

    /** The dish's subcategory as the journal names it; null when it has none. */
    private static Component subcategoryName(ResourceLocation dish) {
        Packets.DishView view = ClientJournalState.dish(dish);
        if (view == null || view.subcategory() == null) return null;
        for (Packets.CollectionView root : ClientJournalState.collections()) {
            for (Packets.SubcategoryView sub : root.subcategories()) {
                if (!sub.id().equals(view.subcategory())) continue;
                if (sub.id().getPath().equals("_mod")) return JournalText.modName(sub.id().getNamespace(), sub.titleKey());
                if (sub.titleKey() == null || sub.titleKey().isEmpty()) return null;
                return sub.literalTitle() ? Component.literal(sub.titleKey()) : Component.translatable(sub.titleKey());
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ today

    private static void renderToday(GuiGraphics graphics, Font font, int left, int right, int cursorY) {
        Packets.SyncStatistics stats = ClientJournalState.statistics();
        Component today = Component.translatable("cuisinejournal.panel.today",
                Component.literal(JournalText.number(stats == null ? 0 : stats.mealsToday()))
                        .withStyle(s -> s.withBold(true).withColor(JournalTheme.TEXT)));
        Component streak = Component.translatable("cuisinejournal.panel.streak",
                Component.literal(JournalText.number(ClientJournalState.cookingStreak()))
                        .withStyle(s -> s.withBold(true).withColor(JournalTheme.TEXT)));
        graphics.drawString(font, today, left, cursorY, JournalTheme.TEXT_DIM, false);
        int streakWidth = font.width(streak);
        if (font.width(today) + 6 + streakWidth <= right - left)
            graphics.drawString(font, streak, right - streakWidth, cursorY, JournalTheme.TEXT_DIM, false);
    }

    // ------------------------------------------------------------------ helpers

    /** 3px bar without a frame, like the journal sidebar's player card. */
    private static void thinBar(GuiGraphics graphics, int x, int y, int width, float fraction) {
        graphics.fill(x, y, x + width, y + 3, JournalTheme.BAR_TRACK);
        int filled = Math.round(Math.max(0F, Math.min(1F, fraction)) * width);
        if (filled > 0) graphics.fillGradient(x, y, x + filled, y + 3, JournalTheme.BAR_FILL_START, JournalTheme.BAR_FILL_END);
    }

    private static void small(GuiGraphics graphics, Font font, Component text, int x, int y, int colour) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(SMALL, SMALL, 1F);
        graphics.drawString(font, text, 0, 0, colour, false);
        graphics.pose().popPose();
    }

    private static void drawTruncated(GuiGraphics graphics, Font font, Component text, int x,
                                      int y, int maxWidth, int colour) {
        if (maxWidth <= 0) return;
        if (font.width(text) <= maxWidth) {
            graphics.drawString(font, text, x, y, colour, false);
            return;
        }
        int dots = font.width("...");
        var clipped = font.substrByWidth(text, Math.max(0, maxWidth - dots));
        graphics.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(clipped), x, y, colour, false);
        graphics.drawString(font, "...", x + font.width(clipped), y, JournalTheme.TEXT_FAINT, false);
    }

    /**
     * Screens that are the player's own pockets rather than a workstation: the
     * survival inventory and the creative tabs.
     */
    private static boolean isPersonalScreen(AbstractContainerMenu menu) {
        if (menu instanceof net.minecraft.world.inventory.InventoryMenu) {
            return true;
        }
        // The creative picker has its own package-private menu class, so it is
        // recognised by the screen instead.
        return Minecraft.getInstance().screen
                instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
    }

    /** Cooking surfaces recognisable from the menu class alone. */
    private static boolean isVanillaCookingMenu(AbstractContainerMenu menu) {
        return menu instanceof net.minecraft.world.inventory.CraftingMenu
                || menu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu;
    }

    /**
     * The dish this panel should talk about.
     *
     * <p>Only dishes this workstation can actually produce qualify. Without
     * that check the ingredients count too — tomatoes and wheat dough are
     * themselves craftable food, so a pot part-way through a recipe would
     * report "Tomato, undiscovered" while cooking something else entirely.</p>
     *
     * <p>Player inventory slots are skipped so the hotbar never hijacks the
     * panel. Output slots win over input slots: a station's own input can be a
     * dish it also makes (a cooking pot holding mochi while it produces a mochi
     * stew), and the finished dish is the one the player cares about. Within
     * each group the dish with more preparations wins.</p>
     */
    private static ResourceLocation focusedDish(AbstractContainerMenu menu) {
        if (menu == null) {
            return null;
        }
        ResourceLocation method = ClientJournalState.menuMethod();
        ResourceLocation bestResult = null, bestInput = null;
        long bestResultCount = -1L, bestInputCount = -1L;
        for (Slot slot : menu.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) {
                continue;
            }
            Packets.DishView dish = ClientJournalState.dish(id);
            if (dish == null || !producedHere(dish, method)) {
                continue;
            }
            long count = preparedCount(id);
            // A result slot refuses placement; an input slot accepts what it holds.
            if (slot.mayPlace(stack)) {
                if (count > bestInputCount) {
                    bestInputCount = count;
                    bestInput = id;
                }
            } else if (count > bestResultCount) {
                bestResultCount = count;
                bestResult = id;
            }
        }
        return bestResult != null ? bestResult : bestInput;
    }

    /**
     * Whether this workstation makes this dish.
     *
     * <p>When the server has not named a method — a configured workstation, or
     * the packet has not landed — nothing is assumed, and the panel simply
     * shows no dish rather than picking one at random.</p>
     */
    private static boolean producedHere(Packets.DishView dish, ResourceLocation method) {
        return method != null && dish.methods().contains(method);
    }

    private static long preparedCount(ResourceLocation dish) {
        Packets.RecordView record = ClientJournalState.record(dish);
        return record == null ? 0L : record.prepared();
    }
}
