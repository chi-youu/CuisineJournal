package com.chi__you.cuisinejournal.client.journal.page;

import com.chi__you.cuisinejournal.client.journal.JournalPage;
import com.chi__you.cuisinejournal.client.journal.JournalScreen;
import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Shared layout bookkeeping for every page: bounds, the font, and the empty
 * state every page needs.
 */
public abstract class BasePage implements JournalPage {

    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected JournalScreen screen;
    protected EditBox localSearch;

    @Override
    public void layout(JournalScreen screen, int x, int y, int width, int height) {
        this.screen = screen;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        onLayout();
    }

    protected void onLayout() {
    }

    protected Font font() {
        return Minecraft.getInstance().font;
    }

    protected int right() {
        return x + width;
    }

    protected int bottom() {
        return y + height;
    }

    /** Draws a wrapped, centred "nothing here yet, and here is what to do" message. */
    protected void renderEmpty(GuiGraphics graphics) {
        Component message = emptyMessage();
        int centreX = x + width / 2;
        int lineY = y + height / 3;
        for (var line : font().split(message, Math.min(width - 16, 220))) {
            int lineWidth = font().width(line);
            graphics.drawString(font(), line, centreX - lineWidth / 2, lineY,
                    JournalTheme.TEXT_DIM, false);
            lineY += 11;
        }
    }

    protected void sectionTitle(GuiGraphics graphics, Component title, int titleY) {
        graphics.drawString(font(), title, x, titleY, JournalTheme.TEXT_ACCENT, false);
        JournalTheme.divider(graphics, x, titleY + 10, width);
    }

    protected void setupLocalSearch(int searchX, int searchY, int searchWidth, Component label,
                                    String value) {
        if (localSearch == null) {
            localSearch = new EditBox(font(), searchX, searchY, searchWidth, 13, label);
            localSearch.setBordered(false);
            localSearch.setMaxLength(128);
            localSearch.setResponder(this::onSearchChanged);
        } else {
            localSearch.setX(searchX);
            localSearch.setY(searchY);
            localSearch.setWidth(searchWidth);
        }
        if (!localSearch.getValue().equals(value == null ? "" : value)) {
            localSearch.setValue(value == null ? "" : value);
        }
    }

    protected void renderLocalSearch(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                     Component hint) {
        if (localSearch == null) return;
        int sx = localSearch.getX();
        int sy = localSearch.getY();
        int sw = localSearch.getWidth();
        graphics.fill(sx, sy, sx + sw, sy + 13, 0xFF0A0705);
        JournalTheme.border(graphics, sx, sy, sw, 13,
                localSearch.isFocused() ? JournalTheme.TEXT_ACCENT : 0xFF4A3A2A);
        localSearch.setHint(hint);
        graphics.enableScissor(sx + 2, sy + 1, sx + sw - 2, sy + 12);
        localSearch.render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();
    }

    protected boolean clickLocalSearch(double mouseX, double mouseY, int button) {
        if (localSearch == null) return false;
        boolean hit = mouseX >= localSearch.getX() && mouseX < localSearch.getX() + localSearch.getWidth()
                && mouseY >= localSearch.getY() && mouseY < localSearch.getY() + 13;
        localSearch.setFocused(hit);
        if (hit) {
            localSearch.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return localSearch != null && localSearch.isFocused()
                && localSearch.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return localSearch != null && localSearch.isFocused()
                && localSearch.charTyped(character, modifiers);
    }

    @Override
    public boolean textInputFocused() {
        return localSearch != null && localSearch.isFocused();
    }

    @Override
    public void tick() {
        if (localSearch != null) localSearch.tick();
    }

    @Override
    public void onHidden() {
        if (localSearch != null) localSearch.setFocused(false);
    }
}
