package com.chi__you.cuisinejournal.client.journal;

import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.client.journal.page.ChallengesPage;
import com.chi__you.cuisinejournal.client.journal.page.CollectionsPage;
import com.chi__you.cuisinejournal.client.journal.page.IngredientsPage;
import com.chi__you.cuisinejournal.client.journal.page.OverviewPage;
import com.chi__you.cuisinejournal.client.journal.page.StatisticsPage;
import com.chi__you.cuisinejournal.network.Network;
import com.chi__you.cuisinejournal.network.Packets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.chi__you.cuisinejournal.client.journal.edit.JournalToast;
import com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * The culinary journal.
 *
 * <p>A persistent sidebar on the left, a clean header with the player's level,
 * and one page's content on the right. Pages manage their own search fields.
 * Panels are drawn in code; icons are item icons from registered mods or small
 * textures where those look better.</p>
 *
 * <p>The layout is proportional and clamped, so it works at any GUI scale and
 * on both narrow and ultrawide windows.</p>
 */
public final class JournalScreen extends Screen {

    private static final int MIN_WIDTH = 320;
    private static final int MAX_WIDTH = 480;
    private static final int MIN_HEIGHT = 200;
    private static final int MAX_HEIGHT = 300;
    private static final int SIDEBAR_WIDTH = 92;
    private static final int HEADER_HEIGHT = 26;
    private static final int PADDING = 8;

    private final List<JournalPage> pages = new ArrayList<>();
    private JournalPage active;

    private int frameX;
    private int frameY;
    private int frameWidth;
    private int frameHeight;

    private JournalToast editToast;
    private int toastBatchOffset;
    private boolean openSoundPlayed;
    public void editToast(Component message, boolean success, boolean waiting) {
        editToast = new JournalToast(message, waiting ? JournalToast.Kind.WAITING
                : success ? JournalToast.Kind.RESULT : JournalToast.Kind.ERROR, success ? 60 : 100, null);
        editToast.show();
    }
    public void toastBatchOffset(int offset) { toastBatchOffset = offset; }

    /**
     * Set while the journal hands the screen to a recipe viewer. The viewer returns to this
     * same instance, so the open page keeps its state (selection, level, scroll).
     */
    private boolean visitingRecipeViewer;

    /** Called by the recipe-viewer bridge around opening JEI/REI/EMI. */
    public void leavingForRecipeViewer(boolean leaving) { visitingRecipeViewer = leaving; }

    public JournalScreen() {
        super(Component.translatable("cuisinejournal.journal.title"));
    }

    @Override
    protected void init() {
        visitingRecipeViewer = false;
        if (!openSoundPlayed) {
            CuisineUiSoundCompat.playOpen();
            CuisineUiSoundCompat.playOpenMessage();
            openSoundPlayed = true;
        }
        // Ask for a fresh profile every time the journal opens: the server is
        // the only source of truth and may have changed while it was closed.
        Network.toServer(new Packets.RequestProfile());

        frameWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width - 40));
        frameHeight = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, height - 40));
        frameX = (width - frameWidth) / 2;
        frameY = (height - frameHeight) / 2;

        if (pages.isEmpty()) {
            // A fresh journal always starts outside edit mode, even if a previous
            // session closed without telling the server.
            if (ClientJournalState.categoryEditing()) {
                Network.toServer(new Packets.CategoryEditModeRequest(false));
            }
            pages.add(new OverviewPage());
            pages.add(new IngredientsPage());
            pages.add(new CollectionsPage());
            pages.add(new ChallengesPage());
            pages.add(new StatisticsPage());
            active = page(com.chi__you.cuisinejournal.config.ClientConfig.normalisePage(
                    com.chi__you.cuisinejournal.config.ConfigManager.client().journal.defaultPage));
        }

        layoutActivePage();
    }

    private void layoutActivePage() {
        if (active == null) {
            return;
        }
        int contentX = frameX + SIDEBAR_WIDTH + PADDING;
        int contentY = frameY + HEADER_HEIGHT + PADDING;
        active.layout(this,
                contentX,
                contentY,
                frameX + frameWidth - PADDING - contentX,
                frameY + frameHeight - PADDING - contentY);
    }

    public void select(JournalPage page) {
        FoodDetailPanel.dismissConfirmation();
        if (active == page) return;
        CuisineUiSoundCompat.page();
        if (active != null) active.onHidden();
        // Any normal page change forgets a pending "Esc returns to …" jump.
        returnTo = null;
        active = page;
        layoutActivePage();
    }

    /**
     * Page to return to on Esc after a cross-page jump (ingredient → its dish in the category
     * page). Cleared by any other navigation, so Esc then behaves normally.
     */
    private JournalPage returnTo;

    /** Opens {@code target} from {@code from}; Esc on {@code target} comes back to {@code from}. */
    public void jump(JournalPage from, JournalPage target) {
        select(target);
        returnTo = from;
    }

    /** The page with this id, or null. */
    public JournalPage page(String id) {
        for (JournalPage page : pages) if (page.id().equals(id)) return page;
        return null;
    }

    /** Called by the page that was jumped to when the player navigates within it. */
    public void clearReturn() {
        returnTo = null;
    }

    public boolean hasReturn() {
        return returnTo != null && returnTo != active;
    }

    /** Esc after a jump: go back to the page the jump came from. */
    private boolean returnFromJump() {
        if (returnTo == null || returnTo == active) { returnTo = null; return false; }
        JournalPage back = returnTo;
        select(back);
        return true;
    }

    public net.minecraft.client.gui.Font font() {
        return font;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        JournalTheme.panel(graphics, frameX, frameY, frameWidth, frameHeight);
        JournalTheme.sidebar(graphics, frameX + 1, frameY + HEADER_HEIGHT,
                SIDEBAR_WIDTH - 1, frameHeight - HEADER_HEIGHT - 1);

        renderHeader(graphics);
        renderSidebar(graphics, mouseX, mouseY);

        if (active != null) {
            if (!ClientJournalState.isCatalogueComplete()) {
                centeredMessage(graphics, Component.translatable(ClientJournalState.directoryFailed()
                        ? "cuisinejournal.sync.failed" : "cuisinejournal.journal.loading"));
            } else {
                active.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (editToast != null) {
            graphics.pose().pushPose(); graphics.pose().translate(0, 0, 700);
            editToast.render(graphics, font, frameX + SIDEBAR_WIDTH + PADDING,
                    frameY + frameHeight - PADDING - 18 - toastBatchOffset,
                    frameWidth - SIDEBAR_WIDTH - PADDING * 2, mouseX, mouseY);
            if (!editToast.isVisible()) editToast = null;
            graphics.pose().popPose();
        }
    }

    private void renderHeader(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("cuisinejournal.journal.title"),
                frameX + PADDING, frameY + 9, JournalTheme.TEXT_ACCENT, false);

        Component level = Component.translatable("cuisinejournal.overview.level")
                .append(" " + ClientJournalState.cookingLevel());
        int right = frameX + frameWidth - PADDING;
        JournalTheme.value(graphics, font, level, right, frameY + 9, JournalTheme.TEXT);

        JournalTheme.divider(graphics, frameX + 1, frameY + HEADER_HEIGHT - 1, frameWidth - 2);
    }

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = frameY + HEADER_HEIGHT + NAV_INSET;
        int tabX = frameX + NAV_INSET, tabWidth = SIDEBAR_WIDTH - NAV_INSET * 2 - 1;
        for (JournalPage page : pages) {
            boolean hovered = mouseX >= tabX && mouseX < tabX + tabWidth
                    && mouseY >= y && mouseY < y + NAV_HEIGHT;
            JournalTheme.card(graphics, tabX, y, tabWidth, NAV_HEIGHT, hovered, page == active);
            // Vanilla item icon (no textures ship with the mod), 12px, no backing square.
            graphics.pose().pushPose();
            graphics.pose().translate(tabX + 5, y + (NAV_HEIGHT - 12) / 2F, 0);
            graphics.pose().scale(0.75F, 0.75F, 1F);
            graphics.renderFakeItem(navIcon(page), 0, 0);
            graphics.pose().popPose();
            graphics.drawString(font, font.plainSubstrByWidth(page.title().getString(), tabWidth - 24),
                    tabX + 21, y + (NAV_HEIGHT - 8) / 2,
                    page == active ? JournalTheme.TEXT_ACCENT : hovered ? JournalTheme.TEXT : JournalTheme.TEXT_DIM, false);
            y += NAV_HEIGHT + NAV_GAP;
        }
        int settingsY = settingsY();
        boolean settingsHover = mouseX >= tabX && mouseX < tabX + tabWidth
                && mouseY >= settingsY && mouseY < settingsY + SETTINGS_HEIGHT;
        if (settingsHover) graphics.fill(tabX, settingsY, tabX + tabWidth, settingsY + SETTINGS_HEIGHT, 0x21FFE9C7);
        drawGear(graphics, tabX + 6, settingsY + 3, settingsHover ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT);
        graphics.drawString(font, Component.translatable("cuisinejournal.settings.open"), tabX + 21, settingsY + 3,
                settingsHover ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT, false);
        renderPlayerCard(graphics, y, settingsY - 4, mouseX, mouseY);
    }

    private static final int NAV_HEIGHT = 20;
    private static final int NAV_GAP = 3;
    /** Space between the tabs/card and the sidebar edges. */
    private static final int NAV_INSET = 5;
    private static final int SETTINGS_HEIGHT = 13;

    private int settingsY() {
        return frameY + frameHeight - NAV_INSET - SETTINGS_HEIGHT;
    }

    /** 7x7 pixel gear, so the settings row needs no texture. */
    private static void drawGear(GuiGraphics graphics, int x, int y, int color) {
        String[] shape = {"..#.#..", ".#####.", "##...##", ".#...#.", "##...##", ".#####.", "..#.#.."};
        for (int row = 0; row < shape.length; row++) for (int col = 0; col < 7; col++)
            if (shape[row].charAt(col) == '#') graphics.fill(x + col, y + row, x + col + 1, y + row + 1, color);
    }

    private static net.minecraft.world.item.ItemStack navIcon(JournalPage page) {
        net.minecraft.world.item.Item item = switch (page.id()) {
            case "overview" -> net.minecraft.world.item.Items.WRITABLE_BOOK;
            case "ingredients" -> net.minecraft.world.item.Items.WHEAT;
            case "collections" -> net.minecraft.world.item.Items.RABBIT_STEW;
            case "challenges" -> net.minecraft.world.item.Items.GOLD_INGOT;
            case "statistics" -> net.minecraft.world.item.Items.CLOCK;
            default -> net.minecraft.world.item.Items.BOOK;
        };
        return new net.minecraft.world.item.ItemStack(item);
    }

    /**
     * Fills the empty bottom of the sidebar: level and XP, title, meals today and the cooking streak.
     * Drawn only when it fits between the tabs and the settings row.
     */
    private void renderPlayerCard(GuiGraphics graphics, int tabsBottom, int bottom, int mouseX, int mouseY) {
        playerCard = null;
        int cardX = frameX + NAV_INSET, cardWidth = SIDEBAR_WIDTH - NAV_INSET * 2 - 1;
        Packets.SyncStatistics stats = ClientJournalState.statistics();
        boolean hasTitle = stats != null && stats.selectedBadge() != null;
        boolean skills = skillsAvailable();
        // The skill row: a divider (4) plus one line (10).
        int cardHeight = 6 + 10 + 7 + (hasTitle ? 12 : 0) + 22 + 3 + (skills ? 14 : 0);
        int cardY = bottom - cardHeight;
        if (cardY < tabsBottom + 4) return;
        boolean hover = skills && mouseX >= cardX && mouseX < cardX + cardWidth && mouseY >= cardY && mouseY < bottom;
        graphics.fill(cardX, cardY, cardX + cardWidth, bottom, hover ? 0xFF1E1610 : 0xFF140F0A);
        JournalTheme.border(graphics, cardX, cardY, cardWidth, cardHeight,
                hover ? JournalTheme.TEXT_ACCENT : 0xFF3A2C1F);
        if (skills) {
            playerCard = new int[]{cardX, cardY, cardWidth, cardHeight};
        }
        int left = cardX + 5, right = cardX + cardWidth - 5, cursor = cardY + 6;
        graphics.drawString(font, "Lv." + ClientJournalState.cookingLevel(), left, cursor, JournalTheme.TEXT_ACCENT, false);
        long into = ClientJournalState.xpIntoLevel(), needed = ClientJournalState.xpForCurrentLevel();
        if (needed > 0) {
            String xp = JournalText.compact(into) + "/" + JournalText.compact(needed);
            graphics.pose().pushPose();
            graphics.pose().translate(right - font.width(xp) * 0.75F, cursor + 2, 0);
            graphics.pose().scale(0.75F, 0.75F, 1F);
            graphics.drawString(font, xp, 0, 0, JournalTheme.TEXT_FAINT, false);
            graphics.pose().popPose();
        }
        cursor += 10;
        JournalTheme.bar(graphics, left, cursor, right - left, 4, needed <= 0 ? 1F : (float) into / needed, needed < 0);
        cursor += 7;
        if (hasTitle) {
            graphics.pose().pushPose();graphics.pose().translate(left,cursor,0);graphics.pose().scale(.625F,.625F,1);
            graphics.renderFakeItem(JournalText.stackOf(JournalText.badgeIcon(stats.selectedBadge())),0,0);graphics.pose().popPose();
            Component title=JournalText.badgeName(stats.selectedBadge());
            graphics.drawString(font,font.plainSubstrByWidth(title.getString(),right-left-13),left+13,cursor,JournalTheme.DISCOVERY,false);
            cursor += 12;
        }
        cursor = cardLine(graphics, left, right, cursor, "cuisinejournal.sidebar.today",
                Component.translatable("cuisinejournal.statistics.dishes",
                        JournalText.number(stats == null ? 0 : stats.mealsToday())));
        cursor = cardLine(graphics, left, right, cursor, "cuisinejournal.sidebar.streak",
                Component.translatable("cuisinejournal.statistics.days",
                        JournalText.number(ClientJournalState.cookingStreak())));
        if (skills) {
            JournalTheme.divider(graphics, left, cursor, right - left);
            cursor += 4;
            Component label = Component.translatable("cuisinejournal.sidebar.skills");
            int color = hover ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT;
            graphics.drawString(font, font.plainSubstrByWidth(label.getString(), right - left - 6), left, cursor, color, false);
            graphics.drawString(font, "›", right - font.width("›"), cursor, color, false);
        }
    }

    /** Bounds of the player card while it doubles as the skill tree entry; null otherwise. */
    private int[] playerCard;

    /** Skills supplies the active tree for the current food mode. */
    private static boolean skillsAvailable() {
        var skills = ClientJournalState.skills();
        return skills != null && skills.active();
    }

    private int cardLine(GuiGraphics graphics, int left, int right, int y, String key, Component value) {
        graphics.drawString(font, Component.translatable(key), left, y, JournalTheme.TEXT_DIM, false);
        JournalTheme.value(graphics, font, value, right, y, JournalTheme.TEXT);
        return y + 11;
    }

    private void centeredMessage(GuiGraphics graphics, Component message) {
        int x = frameX + SIDEBAR_WIDTH + (frameWidth - SIDEBAR_WIDTH) / 2;
        graphics.drawString(font, message, x - font.width(message) / 2,
                frameY + frameHeight / 2, JournalTheme.TEXT_DIM, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editToast != null && editToast.mouseClicked(mouseX, mouseY, button)) return true;
        if (active != null && active.blocksInput()) {
            active.mouseClicked(mouseX, mouseY, button); return true;
        }
        int y = frameY + HEADER_HEIGHT + NAV_INSET;
        int tabX = frameX + NAV_INSET, tabWidth = SIDEBAR_WIDTH - NAV_INSET * 2 - 1;
        for (JournalPage page : pages) {
            if (mouseX >= tabX && mouseX < tabX + tabWidth
                    && mouseY >= y && mouseY < y + NAV_HEIGHT) {
                select(page);
                return true;
            }
            y += NAV_HEIGHT + NAV_GAP;
        }
        if (button == 0 && playerCard != null && skillsAvailable()
                && mouseX >= playerCard[0] && mouseX < playerCard[0] + playerCard[2]
                && mouseY >= playerCard[1] && mouseY < playerCard[1] + playerCard[3]) {
            CuisineUiSoundCompat.click();
            JournalSkillsNavigation.open(this);
            return true;
        }
        if (button == 0 && mouseX >= tabX && mouseX < tabX + tabWidth
                && mouseY >= settingsY() && mouseY < settingsY() + SETTINGS_HEIGHT) {
            CuisineUiSoundCompat.click();
            // Same quiet hand-off as visiting a recipe viewer: page state survives the round trip.
            visitingRecipeViewer = true;
            minecraft.setScreen(new ClientSettingsScreen(this));
            return true;
        }
        if (active != null && active.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if(active!=null&&active.blocksInput()){active.mouseScrolled(mouseX,mouseY,delta);return true;}
        if (active != null && active.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if(active!=null&&active.blocksInput()){active.mouseDragged(mouseX,mouseY,button,dragX,dragY);return true;}
        if (active != null && active.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if(active!=null&&active.blocksInput()){active.mouseReleased(mouseX,mouseY,button);return true;}
        if (active != null && active.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The open-journal key also closes it, from any page or sub-view (unlike Esc, which
        // steps back one layer) — but never while a text field is taking typed letters.
        net.minecraft.client.KeyMapping toggle = com.chi__you.cuisinejournal.forge.ForgeClientBridge.openJournalKey();
        if (toggle != null && toggle.matches(keyCode, scanCode) && !textInputFocused()) {
            onClose();
            return true;
        }
        if (active != null && active.keyPressed(keyCode, scanCode, modifiers)) return true;
        // A modal or menu owns the keyboard; never fall through to tab switching or Esc-close.
        if (active != null && active.blocksInput()) return true;
        // After a cross-page jump, Esc goes back to where the jump came from.
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && returnFromJump()) return true;
        // Keyboard navigation between tabs, so the journal is usable without a
        // mouse.
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB && !hasShiftDown()
                && !textInputFocused()) {
            int index = pages.indexOf(active);
            select(pages.get((index + 1) % pages.size()));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean textInputFocused() {
        return active != null && active.textInputFocused();
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (active != null && active.blocksInput()) return active.charTyped(character, modifiers) || true;
        return active != null && active.charTyped(character, modifiers)
                || super.charTyped(character, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (editToast != null) editToast.tick();
        if (active != null) active.tick();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override public void removed() {
        FoodDetailPanel.dismissConfirmation();
        if (visitingRecipeViewer) {
            // A recipe-viewer round trip is not a page exit: keep the page's browse sessions,
            // selected food, scroll positions, filters and outstanding detail requests.
            super.removed();
            return;
        }
        CuisineUiSoundCompat.playClose();
        if (active != null) active.onHidden();
        // Leaving the journal also leaves edit mode (design §1).
        if (ClientJournalState.categoryEditing()) {
            Network.toServer(new Packets.CategoryEditModeRequest(false));
        }
        super.removed();
    }
}
