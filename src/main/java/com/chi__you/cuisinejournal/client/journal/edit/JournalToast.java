package com.chi__you.cuisinejournal.client.journal.edit;

import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Small non-blocking status toast for asynchronous editor actions (design §9):
 * 14 px high, width = text + 22, centred in the content area, a 2 px colour bar
 * and a 7×7 icon on the left. Results stay 60 ticks (failures 100), fade during
 * the last 10, pause while hovered and disappear on left click.
 */
public final class JournalToast {
    public enum Kind { WAITING, RESULT, ERROR }

    private static final int HEIGHT = 14;
    private static final int FADE_TICKS = 10;
    /** A waiting toast that never gets a result is dropped after the request timeout. */
    private static final int WAITING_TICKS = 220;

    private Component message;
    private Kind kind;
    private int durationTicks;
    private final Runnable dismissAction;
    private int remaining;
    private boolean visible;
    private boolean hovered;
    private int lastX, lastY, lastWidth, lastHeight;

    public JournalToast(Component message, Kind kind, int durationTicks, Runnable dismissAction) {
        this.message = message == null ? Component.empty() : message;
        this.kind = kind == null ? Kind.RESULT : kind;
        this.durationTicks = this.kind == Kind.WAITING ? WAITING_TICKS : Math.max(1, durationTicks);
        this.dismissAction = dismissAction;
        this.remaining = this.durationTicks;
    }

    public void show() { visible = true; remaining = durationTicks; }
    public void hide() { if (visible) { visible = false; if (dismissAction != null) dismissAction.run(); } }
    public boolean isVisible() { return visible; }
    public Kind kind() { return kind; }
    public void setWaiting(boolean waiting) {
        kind = waiting ? Kind.WAITING : Kind.RESULT;
        durationTicks = waiting ? WAITING_TICKS : 60;
        if (waiting) show();
    }
    public void result(Component message, Kind kind) {
        this.message = message == null ? Component.empty() : message;
        this.kind = kind == null ? Kind.RESULT : kind;
        durationTicks = this.kind == Kind.ERROR ? 100 : 60;
        show();
    }

    public void tick() {
        if (!visible || hovered) return;
        if (--remaining <= 0) hide();
    }

    /**
     * @param areaX     left edge of the content area
     * @param y         top of the toast
     * @param areaWidth width of the content area; the toast is centred in it
     */
    public void render(GuiGraphics graphics, Font font, int areaX, int y, int areaWidth, int mouseX, int mouseY) {
        if (!visible) return;
        int maxWidth = Math.max(40, areaWidth - 16);
        int width = Math.min(font.width(message) + 22, maxWidth);
        int drawX = areaX + Math.max(0, (areaWidth - width) / 2);
        lastX = drawX; lastY = y; lastWidth = width; lastHeight = HEIGHT;
        hovered = inside(mouseX, mouseY, drawX, y, width, HEIGHT);

        float alpha = kind == Kind.WAITING || hovered ? 1.0F
                : Mth.clamp(remaining / (float) FADE_TICKS, 0.0F, 1.0F);
        int a = Math.max(4, (int) (alpha * 255.0F));

        graphics.fill(drawX, y, drawX + width, y + HEIGHT, withAlpha(0xFF140F0A, a));
        JournalTheme.border(graphics, drawX, y, width, HEIGHT, withAlpha(JournalTheme.PANEL_BORDER, a));
        int color;
        if (kind == Kind.WAITING) {
            color = JournalTheme.TEXT_FAINT;
        } else {
            color = kind == Kind.ERROR ? JournalTheme.DANGER : JournalTheme.TEXT_GOOD;
            graphics.fill(drawX + 1, y + 1, drawX + 3, y + HEIGHT - 1, withAlpha(color, a));
            PixelIcons.draw(graphics, kind == Kind.ERROR ? PixelIcons.Icon.CLOSE : PixelIcons.Icon.CHECK,
                    drawX + 6, y + 4, withAlpha(color, a));
        }
        int textX = drawX + (kind == Kind.WAITING ? 11 : 16);
        String text = font.plainSubstrByWidth(message.getString(), Math.max(0, drawX + width - 4 - textX));
        graphics.drawString(font, text, textX, y + 3,
                withAlpha(kind == Kind.WAITING ? JournalTheme.TEXT_FAINT : JournalTheme.TEXT, a), false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (visible && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && inside(mouseX, mouseY, lastX, lastY, lastWidth, lastHeight)) { hide(); return true; }
        return false;
    }

    private static int withAlpha(int color, int alpha) {
        int base = (color >>> 24) & 0xFF;
        return ((base * alpha / 255) << 24) | (color & 0xFFFFFF);
    }

    private static boolean inside(double x, double y, int rx, int ry, int width, int height) {
        return x >= rx && x < rx + width && y >= ry && y < ry + height;
    }
}
