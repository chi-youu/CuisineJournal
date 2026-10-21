package com.chi__you.cuisinejournal.client.journal.edit;

import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.awt.Rectangle;
import java.util.List;

/** Context menu for card and grid actions. */
public final class JournalMenu {
    public static class Item {
        private final Component label;
        private final PixelIcons.Icon icon;
        private final boolean danger;
        private final boolean enabled;
        private final Component disabledTip;
        private final Runnable action;
        private final boolean separator;

        public Item(Component label, PixelIcons.Icon icon, boolean danger, boolean enabled,
                    Component disabledTip, Runnable action) {
            this(label, icon, danger, enabled, disabledTip, action, false);
        }

        protected Item(Component label, PixelIcons.Icon icon, boolean danger, boolean enabled,
                       Component disabledTip, Runnable action, boolean separator) {
            this.label = label == null ? Component.empty() : label;
            this.icon = icon == null ? PixelIcons.Icon.NONE : icon;
            this.danger = danger;
            this.enabled = enabled;
            this.disabledTip = disabledTip;
            this.action = action;
            this.separator = separator;
        }

        public static Item separator() {
            return new Item(Component.empty(), PixelIcons.Icon.NONE, false, false, null, null, true);
        }

        public Component label() { return label; }
        public PixelIcons.Icon icon() { return icon; }
        public boolean danger() { return danger; }
        public boolean enabled() { return enabled; }
        public Component disabledTip() { return disabledTip; }
        public boolean isSeparator() { return separator; }
    }

    /** Naming used by page integration; Item remains as a compatibility alias. */
    public static final class Entry extends Item {
        public Entry(Component label, PixelIcons.Icon icon, boolean danger, boolean enabled,
                     Component disabledTip, Runnable action) {
            super(label, icon, danger, enabled, disabledTip, action);
        }

        public static Entry separator() {
            return new Entry(Component.empty(), PixelIcons.Icon.NONE, false, false, null, null, true);
        }

        private Entry(Component label, PixelIcons.Icon icon, boolean danger, boolean enabled,
                      Component disabledTip, Runnable action, boolean separator) {
            super(label, icon, danger, enabled, disabledTip, action, separator);
        }
    }

    private final List<Item> items;
    private Rectangle bounds = new Rectangle();
    private boolean open;
    private Runnable closeAction;

    public JournalMenu(List<? extends Item> items) { this(items, null); }

    public JournalMenu(List<? extends Item> items, Runnable closeAction) {
        this.items = List.copyOf(items == null ? List.of() : items);
        this.closeAction = closeAction;
    }

    public void open(int anchorX, int anchorY, int areaX, int areaY, int areaWidth, int areaHeight, Font font) {
        bounds(font, anchorX, anchorY, areaX, areaY, areaWidth, areaHeight);
    }

    private static final int PAD = 3, ROW = 15, SEPARATOR = 7, ICON_X = 5, TEXT_X = 20;

    public Rectangle bounds(Font font, int anchorX, int anchorY, int areaX, int areaY,
                            int areaWidth, int areaHeight) {
        int width = Math.max(96, TEXT_X + 8 + items.stream().filter(i -> !i.isSeparator())
                .mapToInt(i -> font.width(i.label())).max().orElse(0));
        int height = PAD * 2 + items.stream().mapToInt(i -> i.isSeparator() ? SEPARATOR : ROW).sum();
        int x = anchorX + width <= areaX + areaWidth ? anchorX : anchorX - width;
        int y = anchorY + height <= areaY + areaHeight ? anchorY : anchorY - height;
        bounds = new Rectangle(Math.max(areaX, Math.min(x, areaX + areaWidth - width)),
                Math.max(areaY, Math.min(y, areaY + areaHeight - height)), width, height);
        open = true;
        return new Rectangle(bounds);
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!open) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        JournalTheme.panel(graphics, bounds.x, bounds.y, bounds.width, bounds.height);
        int rowY = bounds.y + PAD;
        Component tip = null;
        for (Item item : items) {
            if (item.isSeparator()) {
                int lineY = rowY + SEPARATOR / 2;
                graphics.fill(bounds.x + 5, lineY, bounds.x + bounds.width - 5, lineY + 1, 0xFF3A2C1F);
                rowY += SEPARATOR;
                continue;
            }
            boolean hovered = inside(mouseX, mouseY, bounds.x, rowY, bounds.width, ROW);
            if (hovered && !item.enabled() && item.disabledTip() != null) tip = item.disabledTip();
            if (hovered && item.enabled()) {
                graphics.fill(bounds.x + 2, rowY, bounds.x + bounds.width - 2, rowY + ROW,
                        item.danger() ? JournalTheme.DANGER_FILL : 0x1AFFE9C7);
            }
            int color = !item.enabled() ? 0xFF4E443A : item.danger() ? JournalTheme.DANGER : JournalTheme.TEXT_DIM;
            PixelIcons.drawCentered(graphics, item.icon(), bounds.x + ICON_X, rowY, 11, ROW, color);
            graphics.drawString(font, item.label(), bounds.x + TEXT_X, rowY + (ROW - font.lineHeight) / 2 + 1, color, false);
            rowY += ROW;
        }
        if (tip != null) graphics.renderTooltip(font, tip, mouseX, mouseY);
        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return true;
        if (!inside(mouseX, mouseY, bounds.x, bounds.y, bounds.width, bounds.height)) {
            close();
            return true;
        }
        int rowY = bounds.y + PAD;
        for (Item item : items) {
            int rowHeight = item.isSeparator() ? SEPARATOR : ROW;
            if (inside(mouseX, mouseY, bounds.x, rowY, bounds.width, rowHeight)) {
                if (!item.isSeparator() && item.enabled() && item.action != null) {
                    com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.click();
                    item.action.run();
                }
                close();
                return true;
            }
            rowY += rowHeight;
        }
        return true;
    }

    public boolean click(double mouseX, double mouseY, int button) { return mouseClicked(mouseX, mouseY, button); }
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return true;
    }
    public boolean isOpen() { return open; }
    public Rectangle bounds() { return new Rectangle(bounds); }
    public void close() {
        if (!open) return;
        open = false;
        if (closeAction != null) closeAction.run();
    }
    private static boolean inside(double x, double y, int rx, int ry, int width, int height) {
        return x >= rx && x < rx + width && y >= ry && y < ry + height;
    }
}
