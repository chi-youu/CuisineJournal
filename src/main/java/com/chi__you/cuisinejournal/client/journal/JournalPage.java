package com.chi__you.cuisinejournal.client.journal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * One tab of the journal.
 *
 * <p>Pages own their own content area and nothing else; the frame draws the
 * sidebar and header; each page owns its search controls. A page is rebuilt on resize rather than
 * caching pixel positions.</p>
 */
public interface JournalPage {

    String id();

    Component title();

    /** Called when the page becomes visible and whenever the window resizes. */
    void layout(JournalScreen screen, int x, int y, int width, int height);

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    default boolean charTyped(char character, int modifiers) { return false; }

    default void tick() { }
    /** An open modal/menu must consume events before the sidebar and widgets. */
    default boolean blocksInput() { return false; }

    /** True while one of the page's own text fields (or a modal's) is receiving typed text. */
    default boolean textInputFocused() { return false; }

    /** Release local input focus and popups without discarding navigation state. */
    default void onHidden() { }

    /** Called by a page's local search field. */
    default void onSearchChanged(String query) {
    }

    default boolean usesSearch() {
        return false;
    }

    /** Rendered when the page has nothing to show; never leave a blank area. */
    default Component emptyMessage() {
        return Component.translatable("cuisinejournal.empty.activity");
    }
}
