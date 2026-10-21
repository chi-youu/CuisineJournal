package com.chi__you.cuisinejournal.client.journal;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Shared journal search styling and input geometry; pages keep their own queries. */
public final class JournalSearchField {
    private JournalSearchField() {}

    /**
     * Text top inside the box. Glyphs are 7px of ink in a 9px line, so 12px boxes use +2 (unchanged
     * for the category page) and taller boxes add the extra space evenly — 13px gets +3.
     */
    private static int textY(int y, int height) {
        return y + Math.max(2, (height - 7) / 2);
    }

    /** Width of the clear (×) area inside the right edge of the box, shown while there is text. */
    private static final int CLEAR_W = 10;

    private static boolean hasText(EditBox field) {
        return !field.getValue().isEmpty();
    }

    public static void bounds(EditBox field, int x, int y, int width, int height) {
        int previousWidth = field.getWidth();
        field.setX(x + 3);
        field.setY(textY(y, height));
        // Text stops short of the × so the two never overlap.
        field.setWidth(Math.max(1, width - 6 - (hasText(field) ? CLEAR_W - 2 : 0)));
        field.setHeight(Math.max(1, height - 4));
        field.setBordered(false);
        field.setHint(Component.empty());
        if (previousWidth != field.getWidth()) field.setHighlightPos(field.getCursorPosition());
    }

    public static void render(GuiGraphics g, Font font, EditBox field, int x, int y,
                              int width, int height, Component hint, int mx, int my, float tick) {
        boolean hover = contains(mx, my, x, y, width, height);
        boolean focused = field.isFocused();
        bounds(field, x, y, width, height);
        g.fill(x, y, x + width, y + height, focused ? 0xFF050302 : 0xFF0A0705);
        JournalTheme.border(g, x, y, width, height, focused ? JournalTheme.TEXT_ACCENT
                : hover ? 0xFF6A563C : 0xFF4A3A2A);
        g.enableScissor(x + 2, y + 1, x + width - 2, y + height - 1);
        field.render(g, mx, my, tick);
        if (field.getValue().isEmpty()) g.drawString(font,
                font.plainSubstrByWidth(hint.getString(), Math.max(1, width - 6)), x + 3, textY(y, height),
                focused ? 0xFF4E443A : JournalTheme.TEXT_FAINT, false);
        g.disableScissor();
        if (hasText(field)) {
            // A small × inside the box, right edge; it only exists while there is something to clear.
            boolean overClear = contains(mx, my, x + width - CLEAR_W - 1, y, CLEAR_W + 1, height);
            int color = overClear ? JournalTheme.TEXT : focused ? JournalTheme.TEXT_DIM : JournalTheme.TEXT_FAINT;
            int cx = x + width - 3 - 5, cy = y + (height - 5) / 2;
            for (int i = 0; i < 5; i++) {
                g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
                g.fill(cx + 4 - i, cy + i, cx + 5 - i, cy + i + 1, color);
            }
        }
    }

    public static boolean click(EditBox field, double mx, double my, int button,
                                 int x, int y, int width, int height) {
        if (button == 0 && hasText(field) && contains(mx, my, x + width - CLEAR_W - 1, y, CLEAR_W + 1, height)) {
            com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.click();
            field.setValue("");
            field.setFocused(true);
            return true;
        }
        boolean hit = contains(mx, my, x, y, width, height);
        field.setFocused(hit);
        if (hit) field.mouseClicked(Math.max(field.getX(), Math.min(field.getX() + field.getWidth() - 1, mx)),
                Math.max(field.getY(), Math.min(field.getY() + field.getHeight() - 1, my)), button);
        return hit;
    }

    private static boolean contains(double mx, double my, int x, int y, int width, int height) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }
}
