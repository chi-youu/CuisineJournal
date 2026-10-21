package com.chi__you.cuisinejournal.client.journal.edit;

import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Optional;

/** A compact journal button with release-to-activate semantics. */
public final class JournalButton implements JournalModal.Focusable {
    public enum Variant { NORMAL, PRIMARY, DANGER, LINK }

    private Component label;
    private final Variant variant;
    private final Runnable action;
    private final int preferredWidth;
    private Component[] tooltip = new Component[0];
    private Rectangle bounds = new Rectangle();
    private PixelIcons.Icon icon = PixelIcons.Icon.NONE;
    private boolean enabled = true;
    private boolean focused;
    private boolean pressed;
    private boolean linkStyle;

    public JournalButton(Component label, Variant variant, Runnable action) {
        this(label, variant, -1, action);
    }

    public JournalButton(Component label, Variant variant, int width, Runnable action) {
        this.label = label == null ? Component.empty() : label;
        this.variant = variant == null ? Variant.NORMAL : variant;
        this.preferredWidth = width;
        this.action = action;
        this.linkStyle = this.variant == Variant.LINK;
    }

    public void setBounds(int x, int y, int width, int height) {
        bounds = new Rectangle(x, y, Math.max(0, width), Math.max(0, height));
    }

    public Rectangle bounds() { return new Rectangle(bounds); }
    public int x() { return bounds.x; }
    public int y() { return bounds.y; }
    public int width() { return bounds.width; }
    public int height() { return bounds.height; }
    public int preferredWidth(Font font) {
        if (preferredWidth >= 0) return preferredWidth;
        return font.width(label) + (icon == PixelIcons.Icon.NONE ? 12 : 22);
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        boolean hovered = contains(mouseX, mouseY);
        int textColor = enabled ? textColor() : 0xFF4E443A;
        if (linkStyle) {
            graphics.drawString(font, label, bounds.x, bounds.y + 3,
                    hovered && enabled ? JournalTheme.TEXT : textColor, false);
            if (hovered && enabled) {
                graphics.fill(bounds.x, bounds.y + bounds.height - 1,
                        bounds.x + Math.min(bounds.width, font.width(label)), bounds.y + bounds.height,
                        JournalTheme.TEXT);
            }
            return;
        }

        int border = enabled ? borderColor() : 0xFF2A2018;
        if (hovered && enabled) {
            graphics.fill(bounds.x + 1, bounds.y + 1, bounds.x + bounds.width - 1,
                    bounds.y + bounds.height - 1, hoverFill());
        }
        if (pressed && enabled) {
            graphics.fill(bounds.x + 1, bounds.y + 1, bounds.x + bounds.width - 1,
                    bounds.y + bounds.height - 1, pressedFill());
        }
        JournalTheme.border(graphics, bounds.x, bounds.y, bounds.width, bounds.height, border);

        int textX = bounds.x + 6;
        if (icon != PixelIcons.Icon.NONE) {
            PixelIcons.draw(graphics, icon, textX, bounds.y + 3, textColor);
            textX += 10;
        }
        int textY = bounds.y + (pressed ? 4 : 3);
        graphics.drawString(font, label, textX, textY, textColor, false);

        if (hovered && enabled && tooltip.length > 0) {
            graphics.renderTooltip(font, Arrays.asList(tooltip), Optional.empty(), mouseX, mouseY);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !enabled) return contains(mouseX, mouseY);
        if (contains(mouseX, mouseY)) {
            pressed = true;
            focused = true;
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        boolean activate = pressed && enabled && contains(mouseX, mouseY);
        pressed = false;
        if (activate) invoke();
        return activate || focused;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (focused && enabled && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE)) {
            invoke();
            return true;
        }
        return false;
    }

    public void invoke() {
        if (enabled && action != null) {
            com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.click();
            action.run();
        }
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= bounds.x && mouseX < bounds.x + bounds.width
                && mouseY >= bounds.y && mouseY < bounds.y + bounds.height;
    }

    private int textColor() {
        return switch (variant) {
            case PRIMARY -> JournalTheme.TEXT_ACCENT;
            case DANGER -> JournalTheme.DANGER;
            case LINK -> JournalTheme.TEXT_DIM;
            case NORMAL -> JournalTheme.TEXT_DIM;
        };
    }

    private int borderColor() {
        return switch (variant) {
            case PRIMARY -> JournalTheme.TEXT_ACCENT;
            case DANGER -> JournalTheme.DANGER;
            case LINK, NORMAL -> 0xFF3A2C1F;
        };
    }

    private int hoverFill() {
        return switch (variant) {
            case PRIMARY -> 0x26E8A93C;
            case DANGER -> JournalTheme.DANGER_FILL;
            case LINK, NORMAL -> 0x1AFFE9C7;
        };
    }

    private int pressedFill() {
        return switch (variant) {
            case PRIMARY -> 0x40E8A93C;
            case DANGER -> 0x55D9634C;
            case LINK, NORMAL -> 0x30FFE9C7;
        };
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; if (!enabled) pressed = false; }
    public boolean isEnabled() { return enabled; }
    @Override public void setFocused(boolean focused) { this.focused = focused; if (!focused) pressed = false; }
    @Override public boolean isFocused() { return focused; }
    public void setTooltip(Component... lines) {
        tooltip = lines == null ? new Component[0] : Arrays.stream(lines)
                .filter(line -> line != null).toArray(Component[]::new);
    }
    public Component[] tooltip() { return tooltip.clone(); }
    public void setIcon(PixelIcons.Icon icon) { this.icon = icon == null ? PixelIcons.Icon.NONE : icon; }
    public PixelIcons.Icon icon() { return icon; }
    public void setLabel(Component label) { this.label = label == null ? Component.empty() : label; }
    public Component label() { return label; }
    public void setLinkStyle(boolean linkStyle) { this.linkStyle = linkStyle; }
    public void setPressed(boolean pressed) { this.pressed = pressed && enabled; }
}
